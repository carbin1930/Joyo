package com.joyo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.ConnectionTestRequest;
import com.joyo.backend.model.MetadataResponses.SchemasResponse;
import com.joyo.backend.model.MetadataResponses.TableDetailResponse;
import com.joyo.backend.model.MetadataResponses.TablesResponse;
import com.joyo.backend.model.SessionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 元数据真实 MySQL 集成测试（spec-02 关键路径：含特殊字符表名、DDL、系统库排序）。
 * 无 Docker 环境时自动跳过。
 */
@Tag("docker")
@EnabledIf(value = "com.joyo.backend.service.MetadataIT#dockerAvailable", disabledReason = "无 Docker 环境")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MetadataIT {

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    private static final String CONN_ID = "meta-it-1";

    @Container
    @SuppressWarnings("resource")
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("joyo_it")
            .withUsername("root")
            .withPassword("root-pass")
            .withInitScript("meta-init.sql");

    private final ConnectionService connectionService = new ConnectionService();
    private final MetadataService metadataService = new MetadataService(connectionService);

    private static ConnectionTestRequest request() {
        return new ConnectionTestRequest(
                MYSQL.getHost(), MYSQL.getMappedPort(3306), "root", "root-pass", "", null);
    }

    @Test
    @Order(1)
    @DisplayName("准备会话（供后续用例使用）")
    void 建会话() {
        SessionResponse session = connectionService.createSession(CONN_ID, request());
        assertThat(session.serverVersion()).contains("8.0");
    }

    @Test
    @Order(2)
    @DisplayName("库列表：包含业务库，系统库排在末尾")
    void 库列表() {
        SchemasResponse resp = metadataService.listSchemas(CONN_ID);
        var names = resp.schemas().stream().map(s -> s.name()).toList();
        assertThat(names).contains("joyo_it");
        assertThat(resp.schemas().get(resp.schemas().size() - 1).isSystem()).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("表列表：含特殊字符表名，类型正确")
    void 表列表() {
        TablesResponse resp = metadataService.listTables(CONN_ID, "joyo_it");
        var names = resp.tables().stream().map(t -> t.name()).toList();
        assertThat(names).contains("order item", "v_user");
        assertThat(resp.tables().stream().filter(t -> t.name().equals("v_user")).findFirst())
                .hasValueSatisfying(t -> assertThat(t.type()).isEqualTo("VIEW"));
    }

    @Test
    @Order(4)
    @DisplayName("表详情：特殊字符表名的列/索引/DDL 正确")
    void 表详情() {
        TableDetailResponse resp = metadataService.tableDetail(CONN_ID, "joyo_it", "order item");
        assertThat(resp.columns()).extracting("name").containsExactly("id", "order_no", "amount");
        assertThat(resp.columns().get(0).key()).isEqualTo("PRI");
        assertThat(resp.indexes()).extracting("name").contains("PRIMARY");
        assertThat(resp.ddl()).startsWith("CREATE TABLE");
    }

    @Test
    @Order(5)
    @DisplayName("不存在的表 → 2101")
    void 表不存在() {
        assertThatThrownBy(() -> metadataService.tableDetail(CONN_ID, "joyo_it", "no_such_table"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.METADATA_FAILED));
    }

    @Test
    @Order(6)
    @DisplayName("无会话的 connectionId → 2004")
    void 无会话() {
        assertThatThrownBy(() -> metadataService.listSchemas("no-session"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SESSION_INVALID));
        connectionService.closeSession(CONN_ID);
    }
}
