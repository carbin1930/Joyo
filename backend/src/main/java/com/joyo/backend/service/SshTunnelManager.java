package com.joyo.backend.service;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.SshTunnelRequest;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SSH 隧道管理（spec-06）：经跳板机建立本地端口转发，隧道生命周期与数据库连接严格绑定。
 * - 连接超时 5s；本地转发端口由系统随机分配（port=0），失败重试 3 次
 * - v0.2 不做 known-hosts 校验（StrictHostKeyChecking=no，spec-06 第 7 节扩展点）
 * - 错误统一归 2005，message 区分：无法连接跳板机 / 认证失败 / 私钥读取失败
 */
@Service
public class SshTunnelManager {

    private static final Logger log = LoggerFactory.getLogger(SshTunnelManager.class);

    /** SSH 连接超时（毫秒），与数据库连接超时同级 */
    static final int SSH_CONNECT_TIMEOUT_MS = 5_000;
    /** 本地转发端口分配失败重试次数（spec-06 行为 2） */
    private static final int FORWARD_RETRIES = 3;

    /** 一条已建立的隧道：SSH 会话 + 本地转发端口；close 先断 SSH（自动关闭转发通道） */
    public record Tunnel(Session session, int localPort) implements AutoCloseable {

        @Override
        public void close() {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    /** 建立隧道并返回本地转发端口；调用方负责 close()。中途失败必须断开已建立的 SSH 会话（spec-06 行为 1） */
    public Tunnel open(SshTunnelRequest ssh, String dbHost, int dbPort) {
        Session session = null;
        try {
            JSch jsch = new JSch();
            if (!ssh.usePasswordAuth()) {
                addIdentity(jsch, ssh);
            }
            session = jsch.getSession(ssh.usernameOrEmpty(), ssh.hostOrEmpty(), ssh.portOrDefault());
            if (ssh.usePasswordAuth()) {
                session.setPassword(ssh.passwordOrEmpty());
            }
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(SSH_CONNECT_TIMEOUT_MS);

            int localPort = forwardWithRetry(session, dbHost, dbPort);
            log.info("SSH 隧道已建立: {}@{}:{} -> 127.0.0.1:{} -> {}:{}",
                    ssh.usernameOrEmpty(), ssh.hostOrEmpty(), ssh.portOrDefault(), localPort, dbHost, dbPort);
            return new Tunnel(session, localPort);
        } catch (BizException e) {
            closeQuietly(session);
            // addIdentity 已分类的 2005 直接透传，避免二次包装
            throw e;
        } catch (Exception e) {
            closeQuietly(session);
            throw classifySsh(e);
        }
    }

    private void closeQuietly(Session session) {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    /** 私钥加载：口令可选；文件不可读 / 格式不支持归"私钥读取失败" */
    private void addIdentity(JSch jsch, SshTunnelRequest ssh) throws JSchException {
        String pass = ssh.passphraseOrEmpty();
        try {
            if (pass.isEmpty()) {
                jsch.addIdentity(ssh.privateKeyPathOrEmpty());
            } else {
                jsch.addIdentity(ssh.privateKeyPathOrEmpty(), pass);
            }
        } catch (JSchException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            // 加载阶段失败 = 文件不存在 / 无法解析 / 口令不正确（jsch 统一抛 "auth fail"）
            if (message.contains("auth fail") && !pass.isEmpty()) {
                throw new BizException(ErrorCode.SSH_TUNNEL_FAILED, "SSH 隧道连接失败：认证失败（私钥口令错误）");
            }
            throw new BizException(ErrorCode.SSH_TUNNEL_FAILED, "SSH 隧道连接失败：私钥读取失败，请重新选择私钥文件");
        }
    }

    /** 随机端口转发，绑定失败重试 3 次（spec-06 行为 2） */
    private int forwardWithRetry(Session session, String dbHost, int dbPort) throws Exception {
        Exception last = null;
        for (int i = 0; i < FORWARD_RETRIES; i++) {
            try {
                return session.setPortForwardingL(0, dbHost, dbPort);
            } catch (Exception e) {
                last = e;
            }
        }
        throw last;
    }

    /**
     * SSH 异常 → 2005 文案分类（spec-06 边界情况表）：
     * 跳板机不可达 → "无法连接跳板机"；认证失败（含口令错误、需要口令）→ "认证失败"；
     * 私钥文件问题 → "私钥读取失败"（在 addIdentity 内处理）。
     * static 供单测覆盖。
     */
    static BizException classifySsh(Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (message.contains("auth fail") || message.contains("auth cancel")) {
            return new BizException(ErrorCode.SSH_TUNNEL_FAILED,
                    "SSH 隧道连接失败：认证失败（密码或私钥口令错误，私钥需要口令时请填写）");
        }
        if (message.contains("timeout") || message.contains("timed out")
                || message.contains("unreachable") || message.contains("refused")
                || message.contains("unknownhost") || message.contains("unknown host")
                || message.contains("connection reset")) {
            return new BizException(ErrorCode.SSH_TUNNEL_FAILED, "SSH 隧道连接失败：无法连接跳板机");
        }
        return new BizException(ErrorCode.SSH_TUNNEL_FAILED,
                "SSH 隧道连接失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    }
}
