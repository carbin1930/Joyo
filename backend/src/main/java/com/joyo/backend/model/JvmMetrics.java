package com.joyo.backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * JVM 资源指标（spec-14，rest-api.md 第 1 节 /health 扩展）。
 * cpuLoad 首次采样（OS bean 无基线）返回负数时为 null，前端显示"--"。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JvmMetrics(Double heapUsedMb, Double heapMaxMb, Double cpuLoad) {
}
