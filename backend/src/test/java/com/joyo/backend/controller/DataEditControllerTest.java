package com.joyo.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.ApiResponse;
import com.joyo.backend.model.MutationModels.MutationRequest;
import com.joyo.backend.model.MutationModels.MutationResponse;
import com.joyo.backend.model.MutationModels.RowChange;
import com.joyo.backend.service.DataEditService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 数据编辑接口（spec-16）：成功包 ApiResponse，业务异常原样上抛由 GlobalExceptionHandler 转换。
 */
class DataEditControllerTest {

    private final DataEditService dataEditService = mock(DataEditService.class);
    private final DataEditController controller = new DataEditController(dataEditService);

    @Test
    void 成功返回applied条数() {
        MutationRequest request = new MutationRequest("users", "db1", List.of("id"),
                List.of(new RowChange("update", Map.of("id", "7"), Map.of("name", "Bob"))));
        when(dataEditService.apply("c1", request)).thenReturn(new MutationResponse(1));

        ApiResponse<MutationResponse> response = controller.apply("c1", request);

        assertEquals(0, response.code());
        assertEquals(1, response.data().applied());
    }

    @Test
    void 服务异常原样上抛() {
        MutationRequest request = new MutationRequest("users", "db1", List.of("id"), List.of());
        when(dataEditService.apply("c1", request))
                .thenThrow(new BizException(ErrorCode.PARAM_INVALID, "没有待保存的改动"));

        BizException e = assertThrows(BizException.class, () -> controller.apply("c1", request));
        assertEquals(1001, e.getErrorCode().getCode());
    }
}
