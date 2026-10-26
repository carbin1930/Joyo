package com.joyo.backend.controller;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.ExportRequest;
import com.joyo.backend.service.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 导出接口（spec-15 第 3 节）：POST /api/v1/export。
 * 响应是文件流而非 ApiResponse JSON；开流前错误（校验/连接/SQL）
 * 仍经 GlobalExceptionHandler 输出标准 JSON（HTTP 200 + 业务码）。
 */
@RestController
@RequestMapping("/api/v1")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(@jakarta.validation.Valid @RequestBody ExportRequest request) {
        validate(request);
        String ext = switch (request.format()) {
            case "csv" -> "csv";
            case "insert" -> "sql";
            default -> "xlsx";
        };
        MediaType mediaType = switch (request.format()) {
            case "csv" -> MediaType.parseMediaType("text/csv;charset=UTF-8");
            case "insert" -> MediaType.parseMediaType("application/sql");
            default -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        };
        StreamingResponseBody body = out -> exportService.write(request, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"export." + ext + "\"")
                .contentType(mediaType)
                .body(body);
    }

    /** 跨字段校验：INSERT 格式必须指定目标表名 */
    private void validate(ExportRequest request) {
        if ("insert".equals(request.format())
                && (request.tableName() == null || request.tableName().isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "INSERT 导出必须指定表名");
        }
    }
}