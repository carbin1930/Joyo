package com.joyo.backend.controller;

import com.joyo.backend.model.ApiResponse;
import com.joyo.backend.model.SyncModels.SyncCompareRequest;
import com.joyo.backend.model.SyncModels.SyncCompareResponse;
import com.joyo.backend.model.SyncModels.SyncExecuteResponse;
import com.joyo.backend.service.SyncService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表/数据同步接口（spec-17，rest-api.md 第 4 节）。
 */
@RestController
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/api/v1/sync/compare")
    public ApiResponse<SyncCompareResponse> compare(@Valid @RequestBody SyncCompareRequest request) {
        return ApiResponse.ok(syncService.compare(request));
    }

    @PostMapping("/api/v1/sync/execute")
    public ApiResponse<SyncExecuteResponse> execute(@Valid @RequestBody SyncCompareRequest request) {
        return ApiResponse.ok(syncService.execute(request));
    }
}
