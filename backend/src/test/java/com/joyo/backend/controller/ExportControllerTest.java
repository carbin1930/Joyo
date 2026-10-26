package com.joyo.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.model.ExportRequest;
import com.joyo.backend.service.ExportService;
import org.junit.jupiter.api.Test;

/**
 * 导出接口跨字段校验（spec-15 第 3/5 节）：INSERT 必须指定表名。
 */
class ExportControllerTest {

    private final ExportController controller = new ExportController(mock(ExportService.class));

    @Test
    void insert缺表名返回1001() {
        ExportRequest request = new ExportRequest("c1", "SELECT 1", "insert", null, " ", null);

        BizException e = assertThrows(BizException.class, () -> controller.export(request));
        assertEquals(1001, e.getErrorCode().getCode());
    }

    @Test
    void csv格式无需表名可通过校验() {
        ExportRequest request = new ExportRequest("c1", "SELECT 1", "csv", null, null, null);

        var response = controller.export(request);
        assertEquals(200, response.getStatusCode().value());
    }
}