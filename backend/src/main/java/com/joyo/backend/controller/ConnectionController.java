package com.joyo.backend.controller;

import com.joyo.backend.model.ApiResponse;
import com.joyo.backend.model.ConnectionTestRequest;
import com.joyo.backend.model.ConnectionTestResponse;
import com.joyo.backend.model.SessionResponse;
import com.joyo.backend.service.ConnectionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 连接管理接口（spec-01，rest-api.md 连接管理节）。
 * 连接配置本身的 CRUD 走 Electron IPC，不经后端。
 */
@RestController
@RequestMapping("/api/v1/connections")
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @PostMapping("/test")
    public ApiResponse<ConnectionTestResponse> test(@Valid @RequestBody ConnectionTestRequest request) {
        return ApiResponse.ok(connectionService.test(request));
    }

    @PostMapping("/{connectionId}/session")
    public ApiResponse<SessionResponse> createSession(
            @PathVariable String connectionId,
            @Valid @RequestBody ConnectionTestRequest request) {
        return ApiResponse.ok(connectionService.createSession(connectionId, request));
    }

    @DeleteMapping("/{connectionId}/session")
    public ApiResponse<Void> closeSession(@PathVariable String connectionId) {
        connectionService.closeSession(connectionId);
        return ApiResponse.ok();
    }
}
