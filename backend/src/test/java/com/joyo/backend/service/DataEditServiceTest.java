package com.joyo.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.MutationModels.MutationRequest;
import com.joyo.backend.model.MutationModels.MutationResponse;
import com.joyo.backend.model.MutationModels.RowChange;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 表数据变更（spec-16 第 4 节）：语句生成、参数绑定、事务提交与回滚。
 */
class DataEditServiceTest {

    private final ConnectionService connectionService = mock(ConnectionService.class);
    private final EditableResolver editableResolver = mock(EditableResolver.class);
    private final DataEditService service = new DataEditService(connectionService, editableResolver);
    private final Connection conn = mock(Connection.class);
    private final PreparedStatement ps = mock(PreparedStatement.class);

    @BeforeEach
    void setUp() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(connectionService.requireDataSource("c1")).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(conn.getAutoCommit()).thenReturn(true);
        when(editableResolver.primaryKey(conn, "db1", "users")).thenReturn(List.of("id"));
        when(editableResolver.tableColumns(conn, "db1", "users"))
                .thenReturn(List.of("id", "name", "age"));
    }

    private MutationRequest request(RowChange... operations) {
        return new MutationRequest("users", "db1", List.of("id"), List.of(operations));
    }

    private String capturedSql() throws SQLException {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(conn).prepareStatement(sql.capture());
        return sql.getValue();
    }

    @Test
    void update按主键定位并绑定参数() throws Exception {
        when(ps.executeUpdate()).thenReturn(1);

        MutationResponse response = service.apply("c1",
                request(new RowChange("update", Map.of("id", "7"), Map.of("name", "Bob"))));

        assertEquals(1, response.applied());
        assertEquals("UPDATE `db1`.`users` SET `name` = ? WHERE `id` = ?", capturedSql());
        verify(ps).setString(1, "Bob");
        verify(ps).setString(2, "7");
        verify(conn).commit();
    }

    @Test
    void update值为null绑定为SQL_NULL() throws Exception {
        when(ps.executeUpdate()).thenReturn(1);
        Map<String, String> values = new HashMap<>();
        values.put("age", null);

        service.apply("c1", request(new RowChange("update", Map.of("id", "7"), values)));

        verify(ps).setNull(1, Types.VARCHAR);
        verify(ps).setString(2, "7");
    }

    @Test
    void delete按主键删除() throws Exception {
        when(ps.executeUpdate()).thenReturn(1);

        service.apply("c1", request(new RowChange("delete", Map.of("id", "9"), null)));

        assertEquals("DELETE FROM `db1`.`users` WHERE `id` = ?", capturedSql());
        verify(ps).setString(1, "9");
    }

    @Test
    void insert按提供列插入() throws Exception {
        when(ps.executeUpdate()).thenReturn(1);
        // 用有序 Map：列顺序决定语句与参数顺序（Map.of 迭代顺序不稳定）
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", "New");
        values.put("age", "18");

        service.apply("c1", request(new RowChange("insert", null, values)));

        String sql = capturedSql();
        assertTrue(sql.startsWith("INSERT INTO `db1`.`users` (`name`, `age`) VALUES (?, ?)"), sql);
        verify(ps).setString(1, "New");
        verify(ps).setString(2, "18");
        verify(conn).commit();
    }

    @Test
    void 改主键列返回4103() {
        BizException e = assertThrows(BizException.class, () -> service.apply("c1",
                request(new RowChange("update", Map.of("id", "7"), Map.of("id", "8")))));

        assertEquals(ErrorCode.PRIMARY_KEY_READONLY.getCode(), e.getErrorCode().getCode());
    }

    @Test
    void 非法列名返回1001() {
        BizException e = assertThrows(BizException.class, () -> service.apply("c1",
                request(new RowChange("update", Map.of("id", "7"), Map.of("nope", "x")))));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getErrorCode().getCode());
    }

    @Test
    void 缺主键值返回1001() {
        BizException e = assertThrows(BizException.class, () -> service.apply("c1",
                request(new RowChange("delete", Map.of(), null))));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getErrorCode().getCode());
    }

    @Test
    void 执行失败整体回滚并返回4102() throws Exception {
        when(ps.executeUpdate()).thenThrow(new SQLException("Duplicate entry 'x' for key 'uk_name'"));

        BizException e = assertThrows(BizException.class, () -> service.apply("c1",
                request(new RowChange("update", Map.of("id", "7"), Map.of("name", "Bob")))));

        assertEquals(ErrorCode.MUTATION_FAILED.getCode(), e.getErrorCode().getCode());
        assertTrue(e.getMessage().contains("Duplicate entry"));
        verify(conn).rollback();
        verify(conn, org.mockito.Mockito.never()).commit();
    }

    @Test
    void 影响0行视为失败并回滚() throws Exception {
        when(ps.executeUpdate()).thenReturn(0);

        BizException e = assertThrows(BizException.class, () -> service.apply("c1",
                request(new RowChange("delete", Map.of("id", "404"), null))));

        assertEquals(ErrorCode.MUTATION_FAILED.getCode(), e.getErrorCode().getCode());
        assertTrue(e.getMessage().contains("未匹配到数据行"));
        verify(conn).rollback();
    }

    @Test
    void 无主键表返回4101() throws Exception {
        when(editableResolver.primaryKey(conn, "db1", "users")).thenReturn(List.of());

        BizException e = assertThrows(BizException.class, () -> service.apply("c1",
                request(new RowChange("update", Map.of("id", "7"), Map.of("name", "Bob")))));

        assertEquals(ErrorCode.NOT_EDITABLE.getCode(), e.getErrorCode().getCode());
    }

    @Test
    void 空改动返回1001() {
        BizException e = assertThrows(BizException.class,
                () -> service.apply("c1", new MutationRequest("users", "db1", List.of("id"), List.of())));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getErrorCode().getCode());
    }
}
