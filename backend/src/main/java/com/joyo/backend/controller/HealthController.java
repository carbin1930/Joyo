package com.joyo.backend.controller;

import com.joyo.backend.model.ApiResponse;
import com.joyo.backend.model.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口：Electron Main 启动后端后轮询此接口确认就绪
 * （docs/10-architecture/overview.md 第 2 节）。
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final String appVersion;
    private final long startMs = System.currentTimeMillis();

    public HealthController(@Value("${joyo.app-version}") String appVersion) {
        this.appVersion = appVersion;
    }

    @GetMapping("/health")
    public ApiResponse<HealthResponse> health() {
        long uptimeSec = (System.currentTimeMillis() - startMs) / 1000;
        return ApiResponse.ok(new HealthResponse("joyo-backend", appVersion, uptimeSec));
    }
}
