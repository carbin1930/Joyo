package com.joyo.backend.controller;

import com.joyo.backend.model.ApiResponse;
import com.joyo.backend.model.LogTailResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日志查看接口（F13 精简版，rest-api.md 预留节）：
 * 返回后端文件日志尾部。日志文件路径与 log4j2.xml 的文件 appender 一致
 * （env:JOYO_LOG_FILE，缺省 logs/backend.log 相对后端工作目录）。
 */
@RestController
@RequestMapping("/api/v1")
public class LogController {

    private static final int DEFAULT_TAIL = 200;
    private static final int MAX_TAIL = 1000;

    private final String logFilePath;

    public LogController(@Value("${JOYO_LOG_FILE:logs/backend.log}") String logFilePath) {
        this.logFilePath = logFilePath;
    }

    @GetMapping("/logs")
    public ApiResponse<LogTailResponse> tail(@RequestParam(defaultValue = "200") int tail) {
        int size = Math.min(Math.max(tail, 1), MAX_TAIL);
        Path path = Path.of(logFilePath);
        if (!Files.exists(path)) {
            return ApiResponse.ok(new LogTailResponse(path.toString(),
                    List.of("(日志文件尚未生成，执行一些操作后再刷新)"), false));
        }
        try {
            List<String> all = Files.readAllLines(path, StandardCharsets.UTF_8);
            boolean truncated = all.size() > size;
            List<String> lines = truncated
                    ? new ArrayList<>(all.subList(all.size() - size, all.size()))
                    : all;
            return ApiResponse.ok(new LogTailResponse(path.toString(), lines, truncated));
        } catch (IOException e) {
            return ApiResponse.ok(new LogTailResponse(path.toString(),
                    List.of("(日志文件读取失败: " + e.getMessage() + ")"), false));
        }
    }
}
