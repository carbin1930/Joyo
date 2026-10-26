package com.joyo.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.joyo.backend.model.MutationModels.EditableInfo;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 可编辑性判定（spec-16 第 4 节）：单表 + 结果含全部主键列。
 */
class EditableResolverTest {

    private final EditableResolver resolver = new EditableResolver();

    /** 元数据桩：给定每列表名 */
    private ResultSetMetaData meta(String... tableNames) throws Exception {
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        when(meta.getColumnCount()).thenReturn(tableNames.length);
        for (int i = 0; i < tableNames.length; i++) {
            when(meta.getTableName(i + 1)).thenReturn(tableNames[i]);
        }
        return meta;
    }

    /** 连接桩：information_schema 查询依次返回给定列名（空数组 = 无主键） */
    private Connection connectionWithPrimaryKeys(String... columns) throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);

        var next = when(rs.next());
        for (int i = 0; i < columns.length; i++) {
            next = next.thenReturn(true);
        }
        next.thenReturn(false);

        if (columns.length > 0) {
            var value = when(rs.getString(1));
            for (String column : columns) {
                value = value.thenReturn(column);
            }
        }
        return conn;
    }

    @Test
    void 单表且含主键可编辑() throws Exception {
        ResultSetMetaData meta = meta("users", "users");
        Connection conn = connectionWithPrimaryKeys("id");

        EditableInfo info = resolver.resolve(conn, meta, List.of("id", "name"), "db1");

        assertNotNull(info);
        assertEquals("db1", info.schema());
        assertEquals("users", info.table());
        assertEquals(List.of("id"), info.primaryKey());
    }

    @Test
    void join或多表不可编辑() throws Exception {
        ResultSetMetaData meta = meta("users", "orders");

        assertNull(resolver.resolve(connectionWithPrimaryKeys("id"), meta, List.of("id", "oid"), "db1"));
    }

    @Test
    void 表达式列表名空串不可编辑() throws Exception {
        ResultSetMetaData meta = meta("users", "");

        assertNull(resolver.resolve(connectionWithPrimaryKeys("id"), meta, List.of("id", "cnt"), "db1"));
    }

    @Test
    void 无主键表不可编辑() throws Exception {
        ResultSetMetaData meta = meta("logs", "logs");

        assertNull(resolver.resolve(connectionWithPrimaryKeys(), meta, List.of("msg"), "db1"));
    }

    @Test
    void 结果未含主键列不可编辑() throws Exception {
        ResultSetMetaData meta = meta("users", "users");
        Connection conn = connectionWithPrimaryKeys("id");

        assertNull(resolver.resolve(conn, meta, List.of("name"), "db1"));
    }

    @Test
    void 联合主键全部出现才可编辑() throws Exception {
        ResultSetMetaData meta = meta("items", "items");
        Connection conn = connectionWithPrimaryKeys("order_id", "sku");

        assertNotNull(resolver.resolve(conn, meta, List.of("sku", "order_id", "qty"), "db1"));
    }
}
