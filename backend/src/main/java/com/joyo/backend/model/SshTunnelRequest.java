package com.joyo.backend.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * SSH 隧道配置（spec-06，rest-api.md 连接管理节）。
 * password / passphrase 为解密后明文，仅经 localhost 回环传输，后端不落盘。
 */
public record SshTunnelRequest(
        Boolean enabled,
        String host,
        @Min(value = 1, message = "SSH 端口范围 1-65535")
        @Max(value = 65535, message = "SSH 端口范围 1-65535") Integer port,
        String username,
        /** "password" | "privateKey"，缺省 password */
        String authType,
        String password,
        String privateKeyPath,
        String passphrase) {

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public String authTypeOrDefault() {
        return authType == null || authType.isBlank() ? "password" : authType;
    }

    public boolean usePasswordAuth() {
        return "password".equals(authTypeOrDefault());
    }

    public String hostOrEmpty() {
        return host == null ? "" : host.trim();
    }

    public int portOrDefault() {
        return port == null ? 22 : port;
    }

    public String usernameOrEmpty() {
        return username == null ? "" : username.trim();
    }

    public String passwordOrEmpty() {
        return password == null ? "" : password;
    }

    public String privateKeyPathOrEmpty() {
        return privateKeyPath == null ? "" : privateKeyPath.trim();
    }

    public String passphraseOrEmpty() {
        return passphrase == null ? "" : passphrase;
    }
}
