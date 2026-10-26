package com.joyo.backend.service;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.MutationModels.MutationRequest;
import com.joyo.backend.model.MutationModels.MutationResponse;
import com.joyo.backend.model.MutationModels.RowChange;
import com.joyo.backend.util.SqlIdentifiers;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 表数据编辑（spec-16，rest-api.md 第 4 节）：
 * - 语句一律后端生成：{@code PreparedStatement} 参数绑定，标识符经 {@link SqlIdentifiers} 反引号转义
 * - 列名按 information_schema 白名单校验（防注入）；主键列只读（改主键 → 4103）
 * - 一个请求一个事务：全部成功 commit，任一条失败 rollback → 4102（message 为 MySQL 原始错误）
 * - UPDATE/DELETE 影响 0 行视为失败（行已被删除或主键值变化），整体回滚
 */
@Service
public class DataEditService {

    private static final Logger log = LoggerFactory.getLogger(DataEditService.class);

    /** 未匹配到数据行：一请求一事务下视为失败，避免"静默无操作" */
    private static final String NO_MATCH_MESSAGE = "未匹配到数据行，可能已被删除";

    private final ConnectionService connectionService;
    private final EditableResolver editableResolver;

    public DataEditService(ConnectionService connectionService, EditableResolver editableResolver) {
        this.connectionService = connectionService;
        this.editableResolver = editableResolver;
    }

    public MutationResponse apply(String connectionId, MutationRequest request) {
        if (request == null || request.table() == null || request.table().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "表名不能为空");
        }
        if (request.operations() == null || request.operations().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "没有待保存的改动");
        }

        HikariDataSource dataSource = connectionService.requireDataSource(connectionId);
        try (Connection conn = dataSource.getConnection()) {
            String schema = request.databaseOrEmpty().isBlank()
                    ? conn.getCatalog()
                    : request.databaseOrEmpty();
            if (schema == null || schema.isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "未指定数据库");
            }
            // 主键与列名以服务端元数据为准（不信任请求里的 primaryKey 顺序与存在性）
            List<String> pk = editableResolver.primaryKey(conn, schema, request.table());
            List<String> columns = editableResolver.tableColumns(conn, schema, request.table());
            if (pk.isEmpty() || columns.isEmpty()) {
                throw new BizException(ErrorCode.NOT_EDITABLE);
            }
            return applyInTransaction(conn, schema, request.table(), pk, columns, request.operations());
        } catch (BizException e) {
            throw e;
        } catch (SQLException e) {
            log.warn("数据编辑连接异常: {}", e.getMessage());
            throw new BizException(ErrorCode.MUTATION_FAILED, message(e));
        }
    }

    private MutationResponse applyInTransaction(
            Connection conn, String schema, String table, List<String> pk,
            List<String> columns, List<RowChange> operations) throws SQLException {
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            int applied = 0;
            for (RowChange op : operations) {
                applied += executeOne(conn, schema, table, pk, columns, op);
            }
            conn.commit();
            log.debug("数据变更提交成功: table={}, 条数={}", table, operations.size());
            return new MutationResponse(applied);
        } catch (BizException e) {
            rollbackQuietly(conn);
            throw e;
        } catch (SQLException e) {
            rollbackQuietly(conn);
            log.warn("数据变更失败已回滚: {}", e.getMessage());
            throw new BizException(ErrorCode.MUTATION_FAILED, message(e));
        } finally {
            try {
                conn.setAutoCommit(autoCommit);
            } catch (SQLException e) {
                log.debug("恢复 autoCommit 失败: {}", e.getMessage());
            }
        }
    }

    private int executeOne(Connection conn, String schema, String table, List<String> pk,
                           List<String> columns, RowChange op) throws SQLException {
        String type = op.type() == null ? "" : op.type().trim().toLowerCase();
        return switch (type) {
            case "update" -> executeUpdate(conn, schema, table, pk, columns, op);
            case "delete" -> executeDelete(conn, schema, table, pk, columns, op);
            case "insert" -> executeInsert(conn, schema, table, columns, op);
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "未知的变更类型: " + type);
        };
    }

    private int executeUpdate(Connection conn, String schema, String table, List<String> pk,
                              List<String> columns, RowChange op) throws SQLException {
        Map<String, String> values = nonNull(op.values());
        if (values.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "修改未包含任何字段");
        }
        for (String col : values.keySet()) {
            if (containsIgnoreCase(pk, col)) {
                throw new BizException(ErrorCode.PRIMARY_KEY_READONLY);
            }
        }
        StringBuilder sql = new StringBuilder("UPDATE ").append(qualified(schema, table)).append(" SET ");
        List<String> setColumns = new ArrayList<>(values.keySet());
        sql.append(String.join(", ", setColumns.stream()
                .map(col -> SqlIdentifiers.quote(resolveColumn(col, columns)) + " = ?").toList()));
        sql.append(" WHERE ").append(whereByPk(pk));

        List<String> params = new ArrayList<>();
        for (String col : setColumns) {
            params.add(values.get(col));
        }
        params.addAll(pkValues(pk, op));

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bind(ps, params);
            int affected = ps.executeUpdate();
            if (affected < 1) {
                throw new BizException(ErrorCode.MUTATION_FAILED, NO_MATCH_MESSAGE);
            }
            return affected;
        }
    }

    private int executeDelete(Connection conn, String schema, String table, List<String> pk,
                              List<String> columns, RowChange op) throws SQLException {
        String sql = "DELETE FROM " + qualified(schema, table) + " WHERE " + whereByPk(pk);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, pkValues(pk, op));
            int affected = ps.executeUpdate();
            if (affected < 1) {
                throw new BizException(ErrorCode.MUTATION_FAILED, NO_MATCH_MESSAGE);
            }
            return affected;
        }
    }

    private int executeInsert(Connection conn, String schema, String table,
                              List<String> columns, RowChange op) throws SQLException {
        Map<String, String> values = nonNull(op.values());
        if (values.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新增行未包含任何字段");
        }
        List<String> insertColumns = new ArrayList<>(values.keySet());
        String columnList = String.join(", ", insertColumns.stream()
                .map(col -> SqlIdentifiers.quote(resolveColumn(col, columns))).toList());
        String placeholders = String.join(", ", insertColumns.stream().map(col -> "?").toList());
        String sql = "INSERT INTO " + qualified(schema, table)
                + " (" + columnList + ") VALUES (" + placeholders + ")";

        List<String> params = new ArrayList<>();
        for (String col : insertColumns) {
            params.add(values.get(col));
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    /** 主键 WHERE 片段：`id` = ? AND ... */
    private String whereByPk(List<String> pk) {
        return String.join(" AND ", pk.stream()
                .map(col -> SqlIdentifiers.quote(col) + " = ?").toList());
    }

    /** 按服务端主键顺序取值；缺任一主键值 → 1001 */
    private List<String> pkValues(List<String> pk, RowChange op) {
        Map<String, String> pkMap = nonNull(op.pk());
        List<String> values = new ArrayList<>(pk.size());
        for (String col : pk) {
            String value = findIgnoreCase(pkMap, col);
            if (value == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "缺少主键值: " + col);
            }
            values.add(value);
        }
        return values;
    }

    /** 参数绑定：null → SQL NULL，其余按字符串（值一律字符串传输，铁律 7） */
    private void bind(PreparedStatement ps, List<String> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            String value = params.get(i);
            if (value == null) {
                ps.setNull(i + 1, Types.VARCHAR);
            } else {
                ps.setString(i + 1, value);
            }
        }
    }

    private String qualified(String schema, String table) {
        return SqlIdentifiers.quote(schema) + "." + SqlIdentifiers.quote(table);
    }

    /** 列名白名单校验（大小写不敏感），返回库内实际列名 */
    private String resolveColumn(String name, List<String> columns) {
        for (String col : columns) {
            if (col.equalsIgnoreCase(name)) {
                return col;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "非法列名: " + name);
    }

    private Map<String, String> nonNull(Map<String, String> map) {
        return map == null ? new LinkedHashMap<>() : map;
    }

    private boolean containsIgnoreCase(List<String> names, String name) {
        return names.stream().anyMatch(n -> n.equalsIgnoreCase(name));
    }

    private String findIgnoreCase(Map<String, String> map, String key) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException e) {
            log.warn("事务回滚失败: {}", e.getMessage());
        }
    }

    private String message(SQLException e) {
        return e.getMessage() == null ? "" : e.getMessage();
    }
}
