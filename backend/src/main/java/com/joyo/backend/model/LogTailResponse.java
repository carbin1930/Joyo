package com.joyo.backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * GET /api/v1/logs 响应（spec-13 第 3 节）。
 * 日志文件不存在时 lines 为空列表、sizeBytes 为 0（前端显示"暂无日志"空态）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogTailResponse(String filePath, long sizeBytes, List<String> lines) {
}
