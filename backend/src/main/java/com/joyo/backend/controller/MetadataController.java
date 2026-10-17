package com.joyo.backend.controller;

import com.joyo.backend.model.ApiResponse;
import com.joyo.backend.model.MetadataResponses.SchemasResponse;
import com.joyo.backend.model.MetadataResponses.TableDetailResponse;
import com.joyo.backend.model.MetadataResponses.TablesResponse;
import com.joyo.backend.service.MetadataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 元数据接口（spec-02，rest-api.md 元数据节）。
 */
@RestController
@RequestMapping("/api/v1/connections/{connectionId}")
public class MetadataController {

    private final MetadataService metadataService;

    public MetadataController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @GetMapping("/schemas")
    public ApiResponse<SchemasResponse> schemas(@PathVariable String connectionId) {
        return ApiResponse.ok(metadataService.listSchemas(connectionId));
    }

    @GetMapping("/schemas/{schema}/tables")
    public ApiResponse<TablesResponse> tables(
            @PathVariable String connectionId,
            @PathVariable String schema) {
        return ApiResponse.ok(metadataService.listTables(connectionId, schema));
    }

    @GetMapping("/schemas/{schema}/tables/{table}")
    public ApiResponse<TableDetailResponse> tableDetail(
            @PathVariable String connectionId,
            @PathVariable String schema,
            @PathVariable String table) {
        return ApiResponse.ok(metadataService.tableDetail(connectionId, schema, table));
    }
}
