package com.joyo.backend.service;

import com.joyo.backend.model.MutationModels.EditableInfo;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 结果可编辑性判定（spec-16 第 4 节）：
 * - 单表：所有列的 {@code ResultSetMetaData.getTableName(i)} 相同且非空（JOIN/表达式/子查询返回空串）
 * - 主键：information_schema.COLUMNS（约定禁用 DatabaseMetaData），且主键列必须全部出现在结果列中
 * 判定失败一律按"不可编辑"处理（安全默认），元数据异常只记日志不外抛。
 */
@Service
public class EditableResolver {

    private static final Logger log = LoggerFactory.getLogger(EditableResolver.class);

    private static final String PK_SQL =
            "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_KEY = 'PRI' "
                    + "ORDER BY ORDINAL_POSITION";

    private static final String COLUMNS_SQL =
            "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";

    /**
     * 判定结果是否可编辑；不可编辑返回 null。
     *
     * @param conn        当前连接（用于查 information_schema）
     * @param meta        结果集元数据
     * @param columnLabels 结果列名（含重复列名加后缀后的标签）
     * @param databaseHint 页签指定库（USE 之前 JDBC 元数据可能仍指向旧库时的兜底）
     */
    public EditableInfo resolve(Connection conn, ResultSetMetaData meta,
                                List<String> columnLabels, String databaseHint) {
        try {
            String table = singleTable(meta);
            if (table == null) {
                return null;
            }
            String schema = resolveSchema(conn, meta, databaseHint);
            if (schema == null || schema.isBlank()) {
                return null;
            }
            List<String> pk = primaryKey(conn, schema, table);
            if (pk.isEmpty()) {
                return null;
            }
            Set<String> labels = new LinkedHashSet<>(columnLabels);
            for (String col : pk) {
                if (!labels.contains(col)) {
                    return null;
                }
            }
            return new EditableInfo(schema, table, pk);
        } catch (SQLException e) {
            log.debug("可编辑性判定失败（按不可编辑处理）: {}", e.getMessage());
            return null;
        }
    }

    /** 所有列来自同一张非空表 → 表名；否则 null */
    String singleTable(ResultSetMetaData meta) throws SQLException {
        String table = null;
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String name = meta.getTableName(i);
            if (name == null || name.isBlank()) {
                return null;
            }
            if (table == null) {
                table = name;
            } else if (!table.equals(name)) {
                return null;
            }
        }
        return table;
    }

    /** 库名：元数据 catalog → 页签指定库 → 连接当前库 */
    private String resolveSchema(Connection conn, ResultSetMetaData meta, String databaseHint)
            throws SQLException {
        if (databaseHint != null && !databaseHint.isBlank()) {
            return databaseHint;
        }
        String catalog = meta.getCatalogName(1);
        if (catalog != null && !catalog.isBlank()) {
            return catalog;
        }
        return conn.getCatalog();
    }

    /** 表主键列（按 ORDINAL_POSITION 排序）；无主键或元数据不可读 → 空列表 */
    public List<String> primaryKey(Connection conn, String schema, String table) {
        return queryColumnNames(conn, PK_SQL, schema, table);
    }

    /** 表全部列名（变更时做列名白名单校验，防注入） */
    public List<String> tableColumns(Connection conn, String schema, String table) {
        return queryColumnNames(conn, COLUMNS_SQL, schema, table);
    }

    private List<String> queryColumnNames(Connection conn, String sql, String schema, String table) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> names = new ArrayList<>();
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
                return names;
            }
        } catch (SQLException e) {
            log.warn("读取表元数据失败: schema={}, table={}, {}", schema, table, e.getMessage());
            return List.of();
        }
    }
}
