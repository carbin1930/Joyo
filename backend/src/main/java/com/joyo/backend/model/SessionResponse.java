package com.joyo.backend.model;

/**
 * POST /connections/{connectionId}/session 成功响应（rest-api.md 连接管理节）。
 */
public record SessionResponse(String connectionId, String serverVersion, String defaultDatabase) {
}
