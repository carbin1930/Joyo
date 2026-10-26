package com.joyo.backend.controller;

import com.joyo.backend.model.ApiResponse;
import com.joyo.backend.model.MutationModels.MutationRequest;
import com.joyo.backend.model.MutationModels.MutationResponse;
import com.joyo.backend.service.DataEditService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表数据编辑接口（spec-16，rest-api.md 第 4 节）。
 */
@RestController
public class DataEditController {

    private final DataEditService dataEditService;

    public DataEditController(DataEditService dataEditService) {
        this.dataEditService = dataEditService;
    }

    @PostMapping("/api/v1/connections/{connectionId}/mutations")
    public ApiResponse<MutationResponse> apply(
            @PathVariable String connectionId,
            @Valid @RequestBody MutationRequest request) {
        return ApiResponse.ok(dataEditService.apply(connectionId, request));
    }
}
