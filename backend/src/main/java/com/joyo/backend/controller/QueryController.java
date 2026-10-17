package com.joyo.backend.controller;

import com.joyo.backend.model.ApiResponse;
import com.joyo.backend.model.QueryModels.QueryExecuteRequest;
import com.joyo.backend.model.QueryModels.QueryExecuteResponse;
import com.joyo.backend.model.QueryModels.RowsPageResponse;
import com.joyo.backend.service.QueryResultCache;
import com.joyo.backend.service.QueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查询执行接口（spec-03，rest-api.md 查询执行节）。
 */
@RestController
public class QueryController {

    private final QueryService queryService;
    private final QueryResultCache resultCache;

    public QueryController(QueryService queryService, QueryResultCache resultCache) {
        this.queryService = queryService;
        this.resultCache = resultCache;
    }

    @PostMapping("/api/v1/connections/{connectionId}/queries")
    public ApiResponse<QueryExecuteResponse> execute(
            @PathVariable String connectionId,
            @Valid @RequestBody QueryExecuteRequest request) {
        return ApiResponse.ok(queryService.execute(connectionId, request));
    }

    @GetMapping("/api/v1/queries/{queryId}/rows")
    public ApiResponse<RowsPageResponse> rows(
            @PathVariable String queryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        return ApiResponse.ok(resultCache.page(queryId, page, pageSize));
    }
}
