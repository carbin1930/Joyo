package com.joyo.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.ConnectionTestRequest;
import com.joyo.backend.model.SshTunnelRequest;
import org.junit.jupiter.api.Test;

/**
 * SSH 隧道单元测试（spec-06）：错误分类与 URL 构造（纯逻辑，不起真实隧道）。
 * 端到端隧道行为由实机验收覆盖（spec-06 第 6 节）。
 */
class SshTunnelManagerTest {

    @Test
    void 认证失败归类为2005() {
        BizException e = SshTunnelManager.classifySsh(new Exception("Auth fail"));
        assertEquals(ErrorCode.SSH_TUNNEL_FAILED, e.getErrorCode());
        assertTrue(e.getMessage().contains("认证失败"));
    }

    @Test
    void 跳板机不可达归类为2005() {
        BizException refused = SshTunnelManager.classifySsh(new Exception("Connection refused"));
        assertEquals(ErrorCode.SSH_TUNNEL_FAILED, refused.getErrorCode());
        assertTrue(refused.getMessage().contains("无法连接跳板机"));

        BizException timeout = SshTunnelManager.classifySsh(new Exception("connect timed out"));
        assertTrue(timeout.getMessage().contains("无法连接跳板机"));
    }

    @Test
    void 未知异常兜底2005() {
        BizException e = SshTunnelManager.classifySsh(new Exception("weird"));
        assertEquals(ErrorCode.SSH_TUNNEL_FAILED, e.getErrorCode());
        assertTrue(e.getMessage().contains("weird"));
    }

    @Test
    void 启用隧道时URL指向本地随机端口() {
        ConnectionTestRequest request = new ConnectionTestRequest(
                "10.0.0.5", 3306, "root", "p", "db", null,
                new SshTunnelRequest(true, "jump", 22, "ubuntu", "password", "pw", null, null));
        assertTrue(request.sshEnabled());

        String url = ConnectionService.buildUrl(request, "127.0.0.1", 45678);
        assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:45678/db?"));
    }

    @Test
    void 未启用隧道用原始主机端口() {
        ConnectionTestRequest request = new ConnectionTestRequest(
                "10.0.0.5", 3306, "root", "p", "", null, null);
        assertFalse(request.sshEnabled());

        String url = ConnectionService.buildUrl(request);
        assertTrue(url.startsWith("jdbc:mysql://10.0.0.5:3306"));
    }

    @Test
    void ssh请求字段缺省值() {
        SshTunnelRequest ssh = new SshTunnelRequest(null, " jump ", null, " ubuntu ", null, null, null, null);
        assertFalse(ssh.isEnabled());
        assertEquals("password", ssh.authTypeOrDefault());
        assertTrue(ssh.usePasswordAuth());
        assertEquals("jump", ssh.hostOrEmpty());
        assertEquals(22, ssh.portOrDefault());
        assertEquals("ubuntu", ssh.usernameOrEmpty());
    }
}
