package com.joyo.backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

/**
 * 表数据编辑 DTO（spec-16，rest-api.md 第 4 节）。
 * 数据库值一律字符串传输（CLAUDE.md 铁律 7）；values 中 JSON null 即 SQL NULL。
 */
public final class MutationModels {

    private MutationModels() {
    }

    /** 结果可编辑信息；响应里为 null 表示不可编辑（前端隐藏编辑入口） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EditableInfo(String schema, String table, List<String> primaryKey) {
    }

    /**
     * 单行变更：
     * - update：pk（主键列名→原值）+ values（列名→新值）
     * - delete：仅 pk
     * - insert：仅 values
     */
    public record RowChange(
            @NotBlank(message = "type 不能为空") String type,
            Map<String, String> pk,
            Map<String, String> values) {
    }

    /** POST /connections/{id}/mutations 请求体：operations 按提交顺序在一个事务内执行 */
    public record MutationRequest(
            @NotBlank(message = "table 不能为空") String table,
            String database,
            List<String> primaryKey,
            @NotEmpty(message = "operations 不能为空") List<RowChange> operations) {

        public String databaseOrEmpty() {
            return database == null ? "" : database;
        }
    }

    /** 变更结果：applied = 实际执行的变更条数 */
    public record MutationResponse(int applied) {
    }
}
