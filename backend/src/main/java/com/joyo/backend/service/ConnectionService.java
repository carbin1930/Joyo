package com.joyo.backend.service;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.ConnectionTestRequest;
import com.joyo.backend.model.ConnectionTestResponse;
import com.joyo.backend.model.SessionResponse;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 连接与会话管理（spec-01，rest-api.md 连接管理节）：
 * - test：临时连接，测完即关
 * - createSession：按 connectionId 惰性建 HikariDataSource 并缓存（data-model.md 第 4 节）
 * - 连接池配置：max=5、connectionTimeout=5s
 * - SSH 隧道（spec-06）：请求带 sshTunnel 时先建隧道，数据源指向 127.0.0.1:{localPort}；
 *   隧道生命周期与会话绑定，断开会话先关隧道再关池
 */
@Service
public class ConnectionService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionService.class);

    /** 连接超时（毫秒），spec-01 边界情况约定 5s */
    static final int CONNECT_TIMEOUT_MS = 5_000;
    static final int MAX_POOL_SIZE = 5;

    /** 会话持有物：连接池 + 可选隧道；关闭顺序：先隧道再池（spec-06 行为 1） */
    private record SessionHandle(HikariDataSource dataSource, SshTunnelManager.Tunnel tunnel) {
    }

    private final Map<String, SessionHandle> sessions = new ConcurrentHashMap<>();

    private final SshTunnelManager sshTunnelManager;

    public ConnectionService(SshTunnelManager sshTunnelManager) {
        this.sshTunnelManager = sshTunnelManager;
    }

    public ConnectionTestResponse test(ConnectionTestRequest request) {
        long start = System.currentTimeMillis();
        if (request.sshEnabled()) {
            try (SshTunnelManager.Tunnel tunnel = sshTunnelManager.open(request.sshTunnel(), request.host(), request.port())) {
                return testDirect(request, "127.0.0.1", tunnel.localPort(), start);
            }
        }
        return testDirect(request, request.host(), request.port(), start);
    }

    private ConnectionTestResponse testDirect(ConnectionTestRequest request, String host, int port, long start) {
        try (Connection conn = DriverManager.getConnection(
                buildUrl(request, host, port), request.username(), request.passwordOrEmpty())) {
            String version = conn.getMetaData().getDatabaseProductVersion();
            return new ConnectionTestResponse(version, System.currentTimeMillis() - start);
        } catch (SQLException e) {
            throw classify(e);
        }
    }

    public SessionResponse createSession(String connectionId, ConnectionTestRequest request) {
        // 幂等：重复建立会话时先销毁旧池（及其隧道）
        closeSession(connectionId);

        SshTunnelManager.Tunnel tunnel = null;
        HikariDataSource dataSource = null;
        String serverVersion;
        try {
            if (request.sshEnabled()) {
                tunnel = sshTunnelManager.open(request.sshTunnel(), request.host(), request.port());
            }
            String host = tunnel == null ? request.host() : "127.0.0.1";
            int port = tunnel == null ? request.port() : tunnel.localPort();

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(buildUrl(request, host, port));
            config.setUsername(request.username());
            config.setPassword(request.passwordOrEmpty());
            config.setMaximumPoolSize(MAX_POOL_SIZE);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(CONNECT_TIMEOUT_MS);
            config.setPoolName("joyo-" + connectionId);

            dataSource = new HikariDataSource(config);
            // 强制建立首个连接，尽早暴露网络/认证问题，而不是留到第一条查询
            try (Connection probe = dataSource.getConnection()) {
                serverVersion = probe.getMetaData().getDatabaseProductVersion();
            }
        } catch (SQLException e) {
            BizException biz = classify(e);
            // 建池失败要关闭半初始化的池与隧道，避免泄漏线程/跳板机连接
            if (dataSource != null) {
                dataSource.close();
            }
            if (tunnel != null) {
                tunnel.close();
            }
            throw biz;
        }

        sessions.put(connectionId, new SessionHandle(dataSource, tunnel));
        log.info("会话已建立: connectionId={}, server={}, viaSsh={}", connectionId, serverVersion, tunnel != null);
        return new SessionResponse(connectionId, serverVersion, request.defaultDatabaseOrEmpty());
    }

    public void closeSession(String connectionId) {
        SessionHandle handle = sessions.remove(connectionId);
        if (handle != null) {
            // spec-06 行为 1：先关隧道再关池
            if (handle.tunnel() != null) {
                handle.tunnel().close();
            }
            handle.dataSource().close();
            log.info("会话已断开: connectionId={}", connectionId);
        }
    }

    /** 供后续查询模块获取会话连接池；会话不存在抛 2004 */
    public HikariDataSource requireDataSource(String connectionId) {
        SessionHandle handle = sessions.get(connectionId);
        if (handle == null || handle.dataSource().isClosed()) {
            throw new BizException(ErrorCode.SESSION_INVALID);
        }
        return handle.dataSource();
    }

    /** JDBC URL 构造：参数化 + 明确超时，杜绝拼接注入（coding-backend.md 第 4 节） */
    static String buildUrl(ConnectionTestRequest request) {
        return buildUrl(request, request.host(), request.port());
    }

    static String buildUrl(ConnectionTestRequest request, String host, int port) {
        String db = request.defaultDatabaseOrEmpty();
        // URLEncoder 是表单编码（空格→+），URL 路径需 %20
        String encodedDb = db.isEmpty() ? ""
                : "/" + java.net.URLEncoder.encode(db, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("+", "%20");
        return "jdbc:mysql://" + host + ":" + port + encodedDb
                + "?connectTimeout=" + CONNECT_TIMEOUT_MS
                + "&socketTimeout=30000"
                + "&sslMode=" + request.sslModeOrDefault()
                + "&allowPublicKeyRetrieval=true"
                + "&characterEncoding=utf8";
    }

    /**
     * SQLException → 业务错误码分类（spec-01 边界情况表）：
     * MySQL 错误 1045 → 2002；超时 → 2003；其余 → 2001。
     */
    static BizException classify(SQLException e) {
        if (e.getErrorCode() == 1045) {
            return new BizException(ErrorCode.AUTH_FAILED, ErrorCode.AUTH_FAILED.getMessage());
        }
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (e instanceof SQLTimeoutException
                || message.contains("timeout")
                || message.contains("timed out")) {
            return new BizException(ErrorCode.CONNECT_TIMEOUT, ErrorCode.CONNECT_TIMEOUT.getMessage());
        }
        return new BizException(ErrorCode.CONNECT_FAILED, ErrorCode.CONNECT_FAILED.getMessage());
    }
}
