package com.joyo.backend.controller;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.ApiResponse;
import com.joyo.backend.model.LogTailResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日志查看接口（spec-13，rest-api.md 第 1 节）：
 * 返回后端文件日志尾部。日志文件路径与 log4j2.xml 的文件 appender 一致
 * （env:JOYO_LOG_FILE，缺省 logs/backend.log 相对后端工作目录）。
 * tail 越界返回 1001（不钳制，暴露调用方错误）；文件不存在返回空 lines（前端空态）。
 */
@RestController
@RequestMapping("/api/v1")
public class LogController {

    static final int DEFAULT_TAIL = 200;
    static final int MAX_TAIL = 2000;

    private final Path logFilePath;

    public LogController(@Value("${JOYO_LOG_FILE:logs/backend.log}") String logFilePath) {
        this.logFilePath = Path.of(logFilePath).toAbsolutePath();
    }

    @GetMapping("/logs")
    public ApiResponse<LogTailResponse> tail(@RequestParam(defaultValue = "200") int tail) {
        if (tail < 1 || tail > MAX_TAIL) {
            throw new BizException(ErrorCode.PARAM_INVALID, "tail 取值范围 1-" + MAX_TAIL);
        }
        if (!Files.exists(logFilePath)) {
            return ApiResponse.ok(new LogTailResponse(logFilePath.toString(), 0, List.of()));
        }
        try {
            long sizeBytes = Files.size(logFilePath);
            return ApiResponse.ok(new LogTailResponse(logFilePath.toString(), sizeBytes, readTailLines(tail)));
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "日志文件读取失败: " + e.getMessage());
        }
    }

    /**
     * 尾部 N 行。日志文件滚动上限 10MB，整体读入后截尾即可；
     * new String 默认 REPLACE 非法 UTF-8 字节，避免个别字节异常导致整次请求失败。
     */
    private List<String> readTailLines(int tail) throws IOException {
        String content = new String(Files.readAllBytes(logFilePath), StandardCharsets.UTF_8);
        List<String> all = Arrays.asList(content.split("\r?\n|\r", -1));
        // split 保留的结尾空串（文件以换行结束产生的空尾行）去掉
        int end = all.size();
        if (end > 0 && all.get(end - 1).isEmpty()) {
            end -= 1;
        }
        int from = Math.max(0, end - tail);
        return all.subList(from, end);
    }
}
