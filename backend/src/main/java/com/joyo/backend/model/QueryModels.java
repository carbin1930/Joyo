package com.joyo.backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 查询执行 DTO（docs/30-api/rest-api.md 查询执行节）。
 * 数据库值一律字符串传输（CLAUDE.md 铁律 7），null 即 JSON null。
 */
public final class QueryModels {

    private QueryModels() {
    }

    /** POST /connections/{id}/queries 请求体 */
    public record QueryExecuteRequest(
            @NotBlank(message = "SQL 不能为空") String sql,
            @Min(value = 1, message = "maxRows 范围 1-10000")
            @Max(value = 10000, message = "maxRows 范围 1-10000") Integer maxRows,
            @Min(value = 1, message = "queryTimeoutSec 范围 1-600")
            @Max(value = 600, message = "queryTimeoutSec 范围 1-600") Integer queryTimeoutSec,
            /** 目标数据库（可选）：非空时执行前先 USE，解决连接池库上下文残留 */
            String database) {

        public int maxRowsOrDefault() {
            return maxRows == null ? 1000 : maxRows;
        }

        public int queryTimeoutOrDefault() {
            return queryTimeoutSec == null ? 30 : queryTimeoutSec;
        }

        public String databaseOrEmpty() {
            return database == null ? "" : database;
        }
    }

    /** 列元信息 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ColumnMeta(String name, String type, boolean nullable) {
    }

    /**
     * 单条语句结果。SELECT 含 queryId/columns/rows/分页信息；
     * DML 含 affectedRows；DDL 仅 affectedRows=0。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StatementResult(
            int ordinal,
            String kind,
            String queryId,
            List<ColumnMeta> columns,
            Boolean totalKnown,
            Long totalRows,
            Boolean truncated,
            List<String[]> rows,
            Integer page,
            Integer pageSize,
            Long affectedRows,
            long elapsedMs) {
    }

    /** 多语句整体响应 */
    public record QueryExecuteResponse(List<StatementResult> statements, long elapsedMs) {
    }

    /** GET /queries/{queryId}/rows 响应 */
    public record RowsPageResponse(
            String queryId, List<String[]> rows, int page, int pageSize,
            boolean totalKnown, long totalRows) {
    }
}
