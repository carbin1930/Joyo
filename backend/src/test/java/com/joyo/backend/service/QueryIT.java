package com.joyo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.ConnectionTestRequest;
import com.joyo.backend.model.QueryModels.QueryExecuteRequest;
import com.joyo.backend.model.QueryModels.QueryExecuteResponse;
import com.joyo.backend.model.QueryModels.StatementResult;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 查询执行真实 MySQL 集成测试（spec-03 关键路径）。
 * 无 Docker 环境时自动跳过。
 */
@Tag("docker")
@EnabledIf(value = "com.joyo.backend.service.QueryIT#dockerAvailable", disabledReason = "无 Docker 环境")
@Testcontainers
class QueryIT {

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    private static final String CONN_ID = "query-it-1";

    @Container
    @SuppressWarnings("resource")
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("joyo_it")
            .withUsername("root")
            .withPassword("root-pass");

    private final ConnectionService connectionService = new ConnectionService(new SshTunnelManager());
    private final QueryResultCache cache = new QueryResultCache();
    private final QueryService queryService = new QueryService(connectionService, cache);

    private QueryExecuteRequest request(String sql, Integer maxRows, Integer timeoutSec) {
        return new QueryExecuteRequest(sql, maxRows, timeoutSec, null);
    }

    @Test
    @DisplayName("SELECT：多行结果、字符串传输、首屏分页")
    void select查询() {
        connectionService.createSession(CONN_ID, new ConnectionTestRequest(
                MYSQL.getHost(), MYSQL.getMappedPort(3306), "root", "root-pass", "joyo_it", null));

        connectionService.requireDataSource(CONN_ID);
        try (var conn = connectionService.requireDataSource(CONN_ID).getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE q_it (id INT PRIMARY KEY, name VARCHAR(32))");
            stmt.execute("INSERT INTO q_it VALUES (1,'a'),(2,'b'),(3,NULL)");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        QueryExecuteResponse resp = queryService.execute(
                CONN_ID, request("SELECT id, name, name FROM q_it ORDER BY id", 2, 30));

        StatementResult s = resp.statements().get(0);
        assertThat(s.kind()).isEqualTo("SELECT");
        assertThat(s.columns()).hasSize(3);
        assertThat(s.columns().get(2).name()).isEqualTo("name_1"); // 重复列名去重
        assertThat(s.truncated()).isTrue();       // maxRows=2 < 3 行
        assertThat(s.totalKnown()).isFalse();
        assertThat(s.rows()).hasSize(2);
        assertThat(s.rows().get(0)).containsExactly("1", "a", "a");
        assertThat(s.rows().get(2 - 1)[1]).isNull(); // NULL → JSON null
        assertThat(s.queryId()).isNotBlank();

        // 翻页取第 2 页
        var page2 = cache.page(s.queryId(), 2, 2);
        assertThat(page2.rows()).hasSize(1);
        assertThat(page2.rows().get(0)[0]).isEqualTo("3");
        connectionService.closeSession(CONN_ID);
    }

    @Test
    @DisplayName("UPDATE 返回影响行数；DDL 返回成功")
    void update与ddl() {
        connectionService.createSession(CONN_ID, new ConnectionTestRequest(
                MYSQL.getHost(), MYSQL.getMappedPort(3306), "root", "root-pass", "joyo_it", null));

        QueryExecuteResponse resp = queryService.execute(CONN_ID,
                request("CREATE TABLE q_it2 (id INT); INSERT INTO q_it2 VALUES (1),(2);", null, 30));

        assertThat(resp.statements().get(0).kind()).isEqualTo("DDL");
        assertThat(resp.statements().get(0).affectedRows()).isEqualTo(0);
        assertThat(resp.statements().get(1).kind()).isEqualTo("UPDATE");
        assertThat(resp.statements().get(1).affectedRows()).isEqualTo(2);
        connectionService.closeSession(CONN_ID);
    }

    @Test
    @DisplayName("语法错误 → 3001 透传 MySQL 原始错误")
    void 语法错误() {
        connectionService.createSession(CONN_ID, new ConnectionTestRequest(
                MYSQL.getHost(), MYSQL.getMappedPort(3306), "root", "root-pass", "joyo_it", null));

        assertThatThrownBy(() -> queryService.execute(CONN_ID, request("SELECT nmae FROM q_it", null, 30)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.QUERY_ERROR);
                    assertThat(e.getMessage()).contains("nmae");
                });
        connectionService.closeSession(CONN_ID);
    }

    @Test
    @DisplayName("多语句第二条失败 → 整体失败，第三条不执行")
    void 多语句中止() {
        connectionService.createSession(CONN_ID, new ConnectionTestRequest(
                MYSQL.getHost(), MYSQL.getMappedPort(3306), "root", "root-pass", "joyo_it", null));

        assertThatThrownBy(() -> queryService.execute(CONN_ID,
                request("CREATE TABLE q_it3 (id INT); SELECT nmae; CREATE TABLE q_it4 (id INT);", null, 30)))
                .isInstanceOf(BizException.class);

        // 第三条未执行：q_it4 不存在 → 建表语句成功执行会通过
        assertThatThrownBy(() -> queryService.execute(CONN_ID,
                request("SELECT * FROM q_it4", null, 30)))
                .isInstanceOf(BizException.class);
        connectionService.closeSession(CONN_ID);
    }

    @Test
    @DisplayName("查询超时 → 3002")
    void 查询超时() {
        connectionService.createSession(CONN_ID, new ConnectionTestRequest(
                MYSQL.getHost(), MYSQL.getMappedPort(3306), "root", "root-pass", "joyo_it", null));

        assertThatThrownBy(() -> queryService.execute(CONN_ID,
                request("SELECT SLEEP(10)", null, 1)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.QUERY_TIMEOUT));
        connectionService.closeSession(CONN_ID);
    }

    @Test
    @DisplayName("空语句请求 → 1001")
    void 空语句() {
        connectionService.createSession(CONN_ID, new ConnectionTestRequest(
                MYSQL.getHost(), MYSQL.getMappedPort(3306), "root", "root-pass", "joyo_it", null));

        assertThatThrownBy(() -> queryService.execute(CONN_ID, request("-- 只有注释", null, 30)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
        connectionService.closeSession(CONN_ID);
    }
}
