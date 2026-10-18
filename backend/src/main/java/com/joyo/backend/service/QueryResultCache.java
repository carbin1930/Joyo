package com.joyo.backend.service;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.QueryModels.RowsPageResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 查询结果内存缓存（data-model.md 第 4 节）：LRU 淘汰，服务结果翻页。
 * 上限：50 个结果集（个人使用足够；行数已由 maxRows ≤ 10000 约束）。
 */
@Component
public class QueryResultCache {

    /** 每页默认/最大行数（rest-api.md 分页约定） */
    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int MAX_PAGE_SIZE = 1000;

    private static final int MAX_ENTRIES = 50;

    /** 缓存的 SELECT 结果（全部已拉取行） */
    public record CachedResult(List<String[]> rows, boolean totalKnown, long totalRows) {
    }

    private final Map<String, CachedResult> cache =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedResult> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };

    public String put(List<String[]> rows, boolean totalKnown, long totalRows) {
        String queryId = UUID.randomUUID().toString();
        synchronized (cache) {
            cache.put(queryId, new CachedResult(rows, totalKnown, totalRows));
        }
        return queryId;
    }

    public CachedResult get(String queryId) {
        synchronized (cache) {
            return cache.get(queryId);
        }
    }

    /** 分页读取；queryId 不存在 → 1002（"结果已过期"）；page/pageSize 越界自动纠正 */
    public RowsPageResponse page(String queryId, int page, int pageSize) {
        CachedResult result = get(queryId);
        if (result == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "结果已过期，请重新执行");
        }
        int normalizedPage = Math.max(page, 1);
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int from = (normalizedPage - 1) * size;
        List<String[]> rows = new ArrayList<>();
        if (from < result.rows().size()) {
            rows = new ArrayList<>(result.rows().subList(from, Math.min(from + size, result.rows().size())));
        }
        return new RowsPageResponse(queryId, rows, normalizedPage, size, result.totalKnown(), result.totalRows());
    }
}
