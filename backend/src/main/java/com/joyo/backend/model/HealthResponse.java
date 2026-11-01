package com.joyo.backend.model;

/**
 * GET /api/v1/health 的响应数据（docs/30-api/rest-api.md 第 1 节）。
 *
 * @param app        应用标识
 * @param version    应用版本
 * @param uptimeSec  已运行秒数
 * @param jvm        JVM 资源指标（spec-14）
 * @param javaVersion 运行后端所用的 Java 版本（"关于"面板展示，如 21.0.12）
 * @param javaVendor  Java 发行方（如 Eclipse Adoptium）
 */
public record HealthResponse(String app, String version, long uptimeSec, JvmMetrics jvm,
                             String javaVersion, String javaVendor) {
}
