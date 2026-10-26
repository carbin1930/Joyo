package com.joyo.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.ApiResponse;
import com.joyo.backend.model.SyncModels;
import com.joyo.backend.model.SyncModels.SyncCompareRequest;
import com.joyo.backend.model.SyncModels.SyncCompareResponse;
import com.joyo.backend.model.SyncModels.SyncExecuteResponse;
import com.joyo.backend.model.SyncModels.TableRef;
import com.joyo.backend.service.SyncService;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 同步接口（spec-17）：成功包 ApiResponse，业务异常原样上抛由 GlobalExceptionHandler 转换。
 */
class SyncControllerTest {

    private final SyncService syncService = mock(SyncService.class);
    private final SyncController controller = new SyncController(syncService);

    private final SyncCompareRequest request = new SyncCompareRequest(
            new TableRef("c1", "db1", "users"), new TableRef("c2", "db2", "users"), 10000);

    @Test
    void compare返回差异结果() {
        SyncCompareResponse data = new SyncCompareResponse(true, null, List.of(), List.of(),
                new SyncModels.DataDiff(1, 2, 3, 10, false), List.of());
        when(syncService.compare(request)).thenReturn(data);

        ApiResponse<SyncCompareResponse> response = controller.compare(request);

        assertEquals(0, response.code());
        assertEquals(2, response.data().dataDiff().update());
    }

    @Test
    void execute返回应用条数() {
        when(syncService.execute(request)).thenReturn(new SyncExecuteResponse(7));

        ApiResponse<SyncExecuteResponse> response = controller.execute(request);

        assertEquals(0, response.code());
        assertEquals(7, response.data().applied());
    }

    @Test
    void 服务异常原样上抛() {
        when(syncService.compare(request))
                .thenThrow(new BizException(ErrorCode.SYNC_NO_PRIMARY_KEY));

        BizException e = assertThrows(BizException.class, () -> controller.compare(request));
        assertEquals(5103, e.getErrorCode().getCode());
    }
}
