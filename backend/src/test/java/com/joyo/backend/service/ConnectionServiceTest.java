package com.joyo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.ConnectionTestRequest;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConnectionServiceTest {

    private static ConnectionTestRequest request(String host, int port) {
        return new ConnectionTestRequest(host, port, "root", "pw", "", null);
    }

    @Test
    @DisplayName("错误分类：MySQL 1045 归为认证失败 2002")
    void 分类_认证失败() {
        SQLException e = new SQLException("Access denied for user", "28000", 1045);
        BizException biz = ConnectionService.classify(e);
        assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.AUTH_FAILED);
    }

    @Test
    @DisplayName("错误分类：SQLTimeoutException 归为连接超时 2003")
    void 分类_超时() {
        SQLException e = new java.sql.SQLTimeoutException("Connection timed out");
        assertThat(ConnectionService.classify(e).getErrorCode()).isEqualTo(ErrorCode.CONNECT_TIMEOUT);
    }

    @Test
    @DisplayName("错误分类：通信失败消息归为超时 2003（JDBC connectTimeout 的典型表现）")
    void 分类_通信超时消息() {
        SQLException e = new SQLException("Communications link failure. The driver has not received "
                + "any packets from the server. connect timed out");
        assertThat(ConnectionService.classify(e).getErrorCode()).isEqualTo(ErrorCode.CONNECT_TIMEOUT);
    }

    @Test
    @DisplayName("错误分类：其余一律归为无法连接 2001")
    void 分类_默认无法连接() {
        SQLException e = new SQLException("Unknown database", "S1009", 1049);
        assertThat(ConnectionService.classify(e).getErrorCode()).isEqualTo(ErrorCode.CONNECT_FAILED);
    }

    @Test
    @DisplayName("JDBC URL：空库不带路径，指定库带编码后的路径")
    void url_构造() {
        String empty = ConnectionService.buildUrl(request("127.0.0.1", 3306));
        assertThat(empty).startsWith("jdbc:mysql://127.0.0.1:3306?");
        assertThat(empty).contains("connectTimeout=5000").contains("sslMode=PREFERRED");

        String withDb = ConnectionService.buildUrl(
                new ConnectionTestRequest("h", 1, "u", "p", "my db", "REQUIRED"));
        assertThat(withDb).contains("jdbc:mysql://h:1/my%20db?");
        assertThat(withDb).contains("sslMode=REQUIRED");
    }
}
