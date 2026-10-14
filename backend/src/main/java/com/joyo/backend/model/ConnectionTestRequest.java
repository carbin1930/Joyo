package com.joyo.backend.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 连接测试 / 建立会话的请求体（docs/30-api/rest-api.md 连接管理节）。
 * 密码仅经 localhost 回环传输，后端不落盘（data-model.md 第 4 节）。
 */
public record ConnectionTestRequest(
        @NotBlank(message = "主机不能为空") String host,
        @NotNull(message = "端口不能为空") @Min(value = 1, message = "端口范围 1-65535")
        @Max(value = 65535, message = "端口范围 1-65535") Integer port,
        @NotBlank(message = "用户名不能为空") String username,
        String password,
        String defaultDatabase,
        /** 缺省 PREFERRED；透传给 JDBC sslMode */
        String sslMode) {

    public String sslModeOrDefault() {
        return sslMode == null || sslMode.isBlank() ? "PREFERRED" : sslMode;
    }

    public String passwordOrEmpty() {
        return password == null ? "" : password;
    }

    public String defaultDatabaseOrEmpty() {
        return defaultDatabase == null ? "" : defaultDatabase;
    }
}
