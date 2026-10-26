package com.joyo.backend.service;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.MetadataResponses.SchemasResponse;
import com.joyo.backend.model.MetadataResponses.SchemasResponse.SchemaItem;
import com.joyo.backend.model.MetadataResponses.TableDetailResponse;
import com.joyo.backend.model.MetadataResponses.TableDetailResponse.TableColumn;
import com.joyo.backend.model.MetadataResponses.TableDetailResponse.TableIndex;
import com.joyo.backend.model.MetadataResponses.TablesResponse;
import com.joyo.backend.model.MetadataResponses.TablesResponse.TableItem;
import com.joyo.backend.util.SqlIdentifiers;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 元数据读取（spec-02）：
 * - 统一走 information_schema + SHOW CREATE TABLE（coding-backend.md 第 4 节，禁用 DatabaseMetaData）
 * - 数据查询一律参数绑定；SHOW CREATE TABLE 的标识符经 SqlIdentifiers 转义
 * - 会话不存在 → 2004；元数据查询失败（含权限不足）→ 2101
 */
@Service
public class MetadataService {

    private static final Logger log = LoggerFactory.getLogger(MetadataService.class);

    /** MySQL 系统库（spec-02 行为细节 1：正常展示但排在后面） */
    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "information_schema", "mysql", "performance_schema", "sys");

    private final ConnectionService connectionService;

    public MetadataService(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    public SchemasResponse listSchemas(String connectionId) {
        return withMetadataConnection(connectionId, conn -> {
            String sql = "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA ORDER BY SCHEMA_NAME";
            List<SchemaItem> schemas = new ArrayList<>();
            List<SchemaItem> systemSchemas = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    SchemaItem item = new SchemaItem(name, SYSTEM_SCHEMAS.contains(name.toLowerCase()));
                    if (item.isSystem()) {
                        systemSchemas.add(item);
                    } else {
                        schemas.add(item);
                    }
                }
            }
            schemas.addAll(systemSchemas);
            return new SchemasResponse(schemas);
        });
    }

    public TablesResponse listTables(String connectionId, String schema) {
        return withMetadataConnection(connectionId, conn -> {
            String sql = "SELECT TABLE_NAME, TABLE_TYPE, ENGINE, TABLE_COMMENT, TABLE_ROWS "
                    + "FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME";
            List<TableItem> tables = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String type = "VIEW".equals(rs.getString("TABLE_TYPE")) ? "VIEW" : "BASE_TABLE";
                        String engine = rs.getString("ENGINE");
                        Long rows = rs.getObject("TABLE_ROWS") == null ? null : rs.getLong("TABLE_ROWS");
                        tables.add(new TableItem(rs.getString("TABLE_NAME"), type, engine,
                                rs.getString("TABLE_COMMENT"), rows));
                    }
                }
            }
            return new TablesResponse(tables);
        });
    }

    public TableDetailResponse tableDetail(String connectionId, String schema, String table) {
        return withMetadataConnection(connectionId, conn -> {
            List<TableColumn> columns = loadColumns(conn, schema, table);
            List<TableIndex> indexes = loadIndexes(conn, schema, table);
            String ddl = loadDdl(conn, schema, table);
            return new TableDetailResponse(columns, indexes, ddl);
        });
    }

    /**
     * spec-17：只读列与索引（不取 DDL）。
     * 用于同步比对——表不存在时 information_schema 返回空列表，不会像 SHOW CREATE TABLE 那样报错。
     */
    public TableDetailResponse structure(Connection conn, String schema, String table)
            throws SQLException {
        return new TableDetailResponse(loadColumns(conn, schema, table), loadIndexes(conn, schema, table), null);
    }

    private List<TableColumn> loadColumns(Connection conn, String schema, String table) throws SQLException {
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE, "
                + "COLUMN_KEY, COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT, ORDINAL_POSITION "
                + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? "
                + "ORDER BY ORDINAL_POSITION";
        List<TableColumn> columns = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Long length = rs.getObject("CHARACTER_MAXIMUM_LENGTH") == null
                            ? null : rs.getLong("CHARACTER_MAXIMUM_LENGTH");
                    String defaultValue = rs.getString("COLUMN_DEFAULT");
                    columns.add(new TableColumn(
                            rs.getString("COLUMN_NAME"),
                            rs.getString("DATA_TYPE"),
                            length,
                            "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                            rs.getString("COLUMN_KEY"),
                            defaultValue,
                            rs.getString("EXTRA"),
                            rs.getString("COLUMN_COMMENT"),
                            rs.getInt("ORDINAL_POSITION")));
                }
            }
        }
        return columns;
    }

    private List<TableIndex> loadIndexes(Connection conn, String schema, String table) throws SQLException {
        String sql = "SELECT INDEX_NAME, INDEX_TYPE, NON_UNIQUE, COLUMN_NAME, SEQ_IN_INDEX "
                + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? "
                + "ORDER BY INDEX_NAME, SEQ_IN_INDEX";
        Map<String, TableIndex> byName = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    String indexType = rs.getString("INDEX_TYPE");
                    boolean unique = rs.getInt("NON_UNIQUE") == 0;
                    String columnName = rs.getString("COLUMN_NAME");
                    TableIndex index = byName.computeIfAbsent(indexName,
                            n -> new TableIndex(n, indexType, unique, new ArrayList<>()));
                    index.columns().add(columnName);
                }
            }
        }
        return new ArrayList<>(byName.values());
    }

    private String loadDdl(Connection conn, String schema, String table) throws SQLException {
        // 标识符无法参数绑定，统一经 SqlIdentifiers 转义（coding-backend.md 第 4 节）
        String sql = "SHOW CREATE TABLE " + SqlIdentifiers.quote(schema) + "." + SqlIdentifiers.quote(table);
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString(2);
            }
            throw new BizException(ErrorCode.METADATA_FAILED, "表不存在或无法读取 DDL");
        }
    }

    /** 统一包装：会话失效 → 2004（由 requireDataSource 抛出）；元数据查询失败 → 2101 */
    private <T> T withMetadataConnection(String connectionId, MetadataQuery<T> query) {
        try (Connection conn = connectionService.requireDataSource(connectionId).getConnection()) {
            return query.run(conn);
        } catch (BizException e) {
            throw e;
        } catch (SQLException e) {
            log.warn("元数据读取失败: connectionId={}, {}", connectionId, e.getMessage());
            throw new BizException(ErrorCode.METADATA_FAILED,
                    ErrorCode.METADATA_FAILED.getMessage() + ": " + e.getMessage());
        } catch (Exception e) {
            throw new BizException(ErrorCode.METADATA_FAILED, ErrorCode.METADATA_FAILED.getMessage());
        }
    }

    @FunctionalInterface
    private interface MetadataQuery<T> {
        T run(Connection conn) throws SQLException;
    }
}
