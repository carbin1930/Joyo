package com.joyo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.ConnectionTestRequest;
import com.joyo.backend.model.ConnectionTestResponse;
import com.joyo.backend.model.SessionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 真实 MySQL 集成测试（coding-backend.md 第 6 节）。
 * 无 Docker 环境时自动跳过：@EnabledIf 检测 Docker 可用性。
 */
@Tag("docker")
@EnabledIf(value = "com.joyo.backend.service.ConnectionIT#dockerAvailable", disabledReason = "无 Docker 环境")
@Testcontainers
class ConnectionIT {

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    @SuppressWarnings("resource")
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("joyo")
            .withUsername("root")
            .withPassword("root-pass");

    private final ConnectionService service = new ConnectionService(new SshTunnelManager());

    private ConnectionTestRequest request(String password) {
        return new ConnectionTestRequest(
                MYSQL.getHost(), MYSQL.getMappedPort(3306), "root", password, "joyo", null);
    }

    @Test
    @DisplayName("连接测试：正确密码成功返回服务器版本")
    void 测试成功() {
        ConnectionTestResponse resp = service.test(request("root-pass"));
        assertThat(resp.serverVersion()).contains("8.0");
        assertThat(resp.elapsedMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("连接测试：错误密码返回 2002")
    void 测试认证失败() {
        assertThatThrownBy(() -> service.test(request("wrong")))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_FAILED));
    }

    @Test
    @DisplayName("会话：建立后 requireDataSource 可用，关闭后抛 2004")
    void 会话生命周期() {
        SessionResponse session = service.createSession("it-conn-1", request("root-pass"));
        assertThat(session.connectionId()).isEqualTo("it-conn-1");
        assertThat(session.serverVersion()).contains("8.0");
        assertThat(service.requireDataSource("it-conn-1")).isNotNull();

        service.closeSession("it-conn-1");
        assertThatThrownBy(() -> service.requireDataSource("it-conn-1"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SESSION_INVALID));
    }

    @Test
    @DisplayName("会话：无法连接的主机建会话返回 2001 且不泄漏资源")
    void 会话_连接失败() {
        ConnectionTestRequest bad = new ConnectionTestRequest(
                "192.0.2.1", 3306, "root", "x", "", null);
        assertThatThrownBy(() -> service.createSession("it-bad", bad))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONNECT_FAILED));
    }
}
