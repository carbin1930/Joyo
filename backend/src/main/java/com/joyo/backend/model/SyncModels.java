package com.joyo.backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 表/数据同步 DTO（spec-17，rest-api.md 第 4 节）。
 * compare 只读取并返回预览；execute 用同一份请求在服务端重新比对后执行（不接受客户端 SQL）。
 */
public final class SyncModels {

    private SyncModels() {
    }

    /** 一侧的表定位（跨连接：源/目标 connectionId 可不同） */
    public record TableRef(
            @NotBlank(message = "connectionId 不能为空") String connectionId,
            @NotBlank(message = "database 不能为空") String database,
            @NotBlank(message = "table 不能为空") String table) {
    }

    public record SyncCompareRequest(
            @Valid @NotNull(message = "source 不能为空") TableRef source,
            @Valid @NotNull(message = "target 不能为空") TableRef target,
            @Min(value = 1, message = "rowLimit 范围 1-1000000")
            @Max(value = 1000000, message = "rowLimit 范围 1-1000000") Integer rowLimit) {

        public int rowLimitOrDefault() {
            return rowLimit == null ? 10_000 : rowLimit;
        }
    }

    /** kind ∈ missing（目标缺）| extra（源缺）| changed（类型/可空不同） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ColumnDiff(String name, String kind, String sourceType, String targetType) {
    }

    /** kind 同 ColumnDiff；detail 为索引列与唯一性描述 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IndexDiff(String name, String kind, String detail) {
    }

    /** 数据差异统计；scanned 为源侧扫描行数，truncated 表示达比对上限 */
    public record DataDiff(int insert, int update, int delete, long scanned, boolean truncated) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SyncCompareResponse(
            boolean comparable,
            String reason,
            List<ColumnDiff> columnDiffs,
            List<IndexDiff> indexDiffs,
            DataDiff dataDiff,
            List<String> statements) {
    }

    public record SyncExecuteResponse(int applied) {
    }
}
