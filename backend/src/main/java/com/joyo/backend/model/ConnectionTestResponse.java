package com.joyo.backend.model;

/**
 * POST /connections/test 成功响应（rest-api.md 连接管理节）。
 *
 * @param serverVersion MySQL 服务器版本，如 "8.0.36"
 * @param elapsedMs     连接耗时毫秒
 */
public record ConnectionTestResponse(String serverVersion, long elapsedMs) {
}
