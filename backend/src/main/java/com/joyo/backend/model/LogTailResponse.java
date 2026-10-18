package com.joyo.backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * GET /api/v1/logs 响应（F13 日志查看精简版，rest-api.md 预留节）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogTailResponse(String path, List<String> lines, boolean truncated) {
}
