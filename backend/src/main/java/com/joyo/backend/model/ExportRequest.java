package com.joyo.backend.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * POST /api/v1/export 请求体（spec-15 第 3 节）。
 * 行数上限范围 1-5,000,000；rowLimit 为空表示全部。
 */
public record ExportRequest(
        @NotBlank String connectionId,
        @NotBlank String sql,
        @NotBlank @Pattern(regexp = "csv|insert|xlsx", message = "format 必须是 csv/insert/xlsx") String format,
        String database,
        String tableName,
        @Min(1) @Max(5_000_000) Integer rowLimit) {
}
