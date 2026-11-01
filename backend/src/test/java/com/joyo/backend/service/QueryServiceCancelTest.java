package com.joyo.backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/**
 * 查询中断（spec-20）：执行期间用 clientQueryId 取消 JDBC 语句。
 */
class QueryServiceCancelTest {

    private final QueryService service = new QueryService(
            mock(ConnectionService.class), mock(QueryResultCache.class), mock(EditableResolver.class));

    /** 反射注入执行中句柄（生产代码由 executeOne 注册） */
    @SuppressWarnings("unchecked")
    private void register(String queryId, Statement stmt) throws Exception {
        var field = QueryService.class.getDeclaredField("running");
        field.setAccessible(true);
        ((ConcurrentHashMap<String, Statement>) field.get(service)).put(queryId, stmt);
    }

    @Test
    void 执行中的查询可中断() throws Exception {
        Statement stmt = mock(Statement.class);
        register("q-1", stmt);

        assertTrue(service.cancel("q-1"));
        verify(stmt).cancel();
    }

    @Test
    void 已结束的查询返回false() {
        assertFalse(service.cancel("not-running"));
    }

    @Test
    void 空queryId抛1001() {
        BizException e = assertThrows(BizException.class, () -> service.cancel("  "));
        assert e.getErrorCode().getCode() == ErrorCode.PARAM_INVALID.getCode();
    }
}
