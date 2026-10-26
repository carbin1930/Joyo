package com.joyo.backend.service;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.MetadataResponses.TableDetailResponse;
import com.joyo.backend.model.MetadataResponses.TableDetailResponse.TableColumn;
import com.joyo.backend.model.MetadataResponses.TableDetailResponse.TableIndex;
import com.joyo.backend.model.SyncModels;
import com.joyo.backend.model.SyncModels.ColumnDiff;
import com.joyo.backend.model.SyncModels.DataDiff;
import com.joyo.backend.model.SyncModels.IndexDiff;
import com.joyo.backend.model.SyncModels.SyncCompareRequest;
import com.joyo.backend.model.SyncModels.SyncCompareResponse;
import com.joyo.backend.model.SyncModels.SyncExecuteResponse;
import com.joyo.backend.util.SqlIdentifiers;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 表/数据同步（spec-17，rest-api.md 第 4 节）：
 * - 结构比对：复用 {@link MetadataService#structure} 读两侧列与索引，按名比对出 missing / extra / changed
 * - 数据比对：基于主键 + {@code MD5(CONCAT_WS(...))} 校验和，只回传主键与校验和，避免全量行传输；
 *   NULL 归一为 {@value #NULL_MARK}，保证 NULL 与空串可区分；比对列取两侧交集
 * - 执行：结构脚本逐条执行（MySQL DDL 隐式提交）→ 数据 INSERT/UPDATE/DELETE 在一个事务内参数化执行，
 *   任一条失败仅回滚数据部分 → 5102
 * - 请求体里不接受 SQL：execute 用与 compare 相同的入参在服务端重新比对后再执行
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    /** 按主键批量取行的批次大小（WHERE ... OR 展开） */
    static final int FETCH_BATCH = 200;
    /** NULL 归一标记（校验和与展示用） */
    static final String NULL_MARK = "#NULL#";
    /** 主键值拼接分隔符（控制字符，避免与值冲突） */
    private static final String KEY_SEP = String.valueOf((char) 31);

    private final ConnectionService connectionService;
    private final MetadataService metadataService;
    private final EditableResolver editableResolver;

    public SyncService(ConnectionService connectionService, MetadataService metadataService,
                       EditableResolver editableResolver) {
        this.connectionService = connectionService;
        this.metadataService = metadataService;
        this.editableResolver = editableResolver;
    }

    public SyncCompareResponse compare(SyncCompareRequest request) {
        validate(request);
        try (Connection sourceConn = connectionOf(request.source());
             Connection targetConn = connectionOf(request.target())) {
            TableDetailResponse source = metadataService.structure(
                    sourceConn, request.source().database(), request.source().table());
            TableDetailResponse target = metadataService.structure(
                    targetConn, request.target().database(), request.target().table());

            List<ColumnDiff> columnDiffs = diffColumns(source.columns(), target.columns());
            List<IndexDiff> indexDiffs = diffIndexes(source.indexes(), target.indexes());
            List<String> statements = new ArrayList<>(
                    structureStatements(request.target(), columnDiffs, indexDiffs));

            if (source.columns().isEmpty()) {
                return notComparable("源表不存在", columnDiffs, indexDiffs, statements);
            }
            if (target.columns().isEmpty()) {
                return notComparable("目标表不存在", columnDiffs, indexDiffs, statements);
            }

            List<String> pk = editableResolver.primaryKey(
                    targetConn, request.target().database(), request.target().table());
            if (pk.isEmpty()) {
                return notComparable("表无主键，无法按主键比对数据", columnDiffs, indexDiffs, statements);
            }
            Set<String> sourceNames = names(source.columns());
            Set<String> targetNames = names(target.columns());
            for (String col : pk) {
                if (!sourceNames.contains(col) || !targetNames.contains(col)) {
                    return notComparable("主键列缺失: " + col, columnDiffs, indexDiffs, statements);
                }
            }

            List<String> valueColumns = valueColumns(source.columns(), targetNames, pk);
            int limit = request.rowLimitOrDefault();
            Map<String, String> sourceChecks =
                    checksums(sourceConn, request.source(), pk, valueColumns, limit);
            Map<String, String> targetChecks =
                    checksums(targetConn, request.target(), pk, valueColumns, limit);
            List<String> insertKeys = new ArrayList<>();
            List<String> updateKeys = new ArrayList<>();
            List<String> deleteKeys = new ArrayList<>();
            for (Map.Entry<String, String> entry : sourceChecks.entrySet()) {
                String check = targetChecks.get(entry.getKey());
                if (check == null) {
                    insertKeys.add(entry.getKey());
                } else if (!check.equals(entry.getValue())) {
                    updateKeys.add(entry.getKey());
                }
            }
            for (String key : targetChecks.keySet()) {
                if (!sourceChecks.containsKey(key)) {
                    deleteKeys.add(key);
                }
            }

            DataDiff dataDiff = new DataDiff(insertKeys.size(), updateKeys.size(), deleteKeys.size(),
                    sourceChecks.size(),
                    sourceChecks.size() >= limit || targetChecks.size() >= limit);
            statements.add(String.format(
                    "-- 数据：新增 %d 行 / 修改 %d 行 / 删除 %d 行（执行时按主键生成并参数化执行）",
                    insertKeys.size(), updateKeys.size(), deleteKeys.size()));
            if (dataDiff.truncated()) {
                statements.add("-- 注意：已达比对行数上限 " + limit + " 行，结果可能不完整");
            }
            log.debug("同步比对完成: {}.{} → {}.{}, 增{} 改{} 删{}",
                    request.source().database(), request.source().table(),
                    request.target().database(), request.target().table(),
                    insertKeys.size(), updateKeys.size(), deleteKeys.size());
            return new SyncCompareResponse(true, null, columnDiffs, indexDiffs, dataDiff, statements);
        } catch (SQLException e) {
            throw QueryService.classify(e);
        }
    }

    public SyncExecuteResponse execute(SyncCompareRequest request) {
        validate(request);
        try (Connection sourceConn = connectionOf(request.source());
             Connection targetConn = connectionOf(request.target())) {
            TableDetailResponse source = metadataService.structure(
                    sourceConn, request.source().database(), request.source().table());
            TableDetailResponse target = metadataService.structure(
                    targetConn, request.target().database(), request.target().table());
            if (source.columns().isEmpty() || target.columns().isEmpty()) {
                throw new BizException(ErrorCode.SYNC_NOT_COMPARABLE, "源表或目标表不存在");
            }
            List<String> pk = editableResolver.primaryKey(
                    targetConn, request.target().database(), request.target().table());
            if (pk.isEmpty()) {
                throw new BizException(ErrorCode.SYNC_NO_PRIMARY_KEY);
            }
            Set<String> targetNames = names(target.columns());
            List<String> valueColumns = valueColumns(source.columns(), targetNames, pk);
            List<String> dataColumns = new ArrayList<>(pk);
            dataColumns.addAll(valueColumns);

            List<String> structure = structureStatements(request.target(),
                    diffColumns(source.columns(), target.columns()),
                    diffIndexes(source.indexes(), target.indexes()));

            int limit = request.rowLimitOrDefault();
            Map<String, String> sourceChecks =
                    checksums(sourceConn, request.source(), pk, valueColumns, limit);
            Map<String, String> targetChecks =
                    checksums(targetConn, request.target(), pk, valueColumns, limit);
            List<String> insertKeys = new ArrayList<>();
            List<String> updateKeys = new ArrayList<>();
            List<String> deleteKeys = new ArrayList<>();
            for (Map.Entry<String, String> entry : sourceChecks.entrySet()) {
                String check = targetChecks.get(entry.getKey());
                if (check == null) {
                    insertKeys.add(entry.getKey());
                } else if (!check.equals(entry.getValue())) {
                    updateKeys.add(entry.getKey());
                }
            }
            for (String key : targetChecks.keySet()) {
                if (!sourceChecks.containsKey(key)) {
                    deleteKeys.add(key);
                }
            }

            // 1) 结构：DDL 逐条执行（MySQL DDL 隐式提交，无法回滚）
            for (String ddl : structure) {
                try (Statement stmt = targetConn.createStatement()) {
                    stmt.execute(ddl);
                }
            }
            // 2) 数据：一个事务
            int applied = applyDataChanges(sourceConn, targetConn, request, pk, valueColumns,
                    dataColumns, insertKeys, updateKeys, deleteKeys);
            log.info("同步执行完成: 结构 {} 条, 数据 {} 条", structure.size(), applied);
            return new SyncExecuteResponse(applied);
        } catch (BizException e) {
            throw e;
        } catch (SQLException e) {
            throw new BizException(ErrorCode.SYNC_FAILED, message(e));
        }
    }

    // ===== 比对 =====

    /** 列差异：目标缺 → missing；源缺 → extra；类型/可空不同 → changed */
    List<ColumnDiff> diffColumns(List<TableColumn> source, List<TableColumn> target) {
        Map<String, TableColumn> targetByName = new LinkedHashMap<>();
        for (TableColumn col : target) {
            targetByName.put(col.name(), col);
        }
        Set<String> sourceNames = new LinkedHashSet<>();
        List<ColumnDiff> diffs = new ArrayList<>();
        for (TableColumn col : source) {
            sourceNames.add(col.name());
            TableColumn other = targetByName.get(col.name());
            if (other == null) {
                diffs.add(new ColumnDiff(col.name(), "missing", typeOf(col), null));
            } else if (!typeOf(col).equals(typeOf(other)) || col.nullable() != other.nullable()) {
                diffs.add(new ColumnDiff(col.name(), "changed", typeOf(col), typeOf(other)));
            }
        }
        for (TableColumn col : target) {
            if (!sourceNames.contains(col.name())) {
                diffs.add(new ColumnDiff(col.name(), "extra", null, typeOf(col)));
            }
        }
        return diffs;
    }

    /** 索引差异：源有目标无 → missing；定义不同 → changed；目标多出 → extra */
    List<IndexDiff> diffIndexes(List<TableIndex> source, List<TableIndex> target) {
        Map<String, TableIndex> targetByName = new LinkedHashMap<>();
        for (TableIndex idx : target) {
            targetByName.put(idx.name(), idx);
        }
        Set<String> sourceNames = new LinkedHashSet<>();
        List<IndexDiff> diffs = new ArrayList<>();
        for (TableIndex idx : source) {
            sourceNames.add(idx.name());
            TableIndex other = targetByName.get(idx.name());
            if (other == null) {
                diffs.add(new IndexDiff(idx.name(), "missing", describe(idx)));
            } else if (!describe(idx).equals(describe(other)) || idx.unique() != other.unique()) {
                diffs.add(new IndexDiff(idx.name(), "changed", describe(idx) + " → " + describe(other)));
            }
        }
        for (TableIndex idx : target) {
            if (!sourceNames.contains(idx.name())) {
                diffs.add(new IndexDiff(idx.name(), "extra", describe(idx)));
            }
        }
        return diffs;
    }

    /** 结构脚本：目标缺列 → ADD COLUMN；列定义变更 → MODIFY COLUMN；源有目标无的索引 → CREATE [UNIQUE] INDEX */
    List<String> structureStatements(SyncModels.TableRef target, List<ColumnDiff> columnDiffs,
                                     List<IndexDiff> indexDiffs) {
        String table = SqlIdentifiers.quote(target.database()) + "." + SqlIdentifiers.quote(target.table());
        List<String> statements = new ArrayList<>();
        for (ColumnDiff diff : columnDiffs) {
            if ("missing".equals(diff.kind())) {
                statements.add("ALTER TABLE " + table + " ADD COLUMN "
                        + SqlIdentifiers.quote(diff.name()) + " " + diff.sourceType());
            } else if ("changed".equals(diff.kind())) {
                statements.add("ALTER TABLE " + table + " MODIFY COLUMN "
                        + SqlIdentifiers.quote(diff.name()) + " " + diff.sourceType());
            }
        }
        for (IndexDiff diff : indexDiffs) {
            if ("missing".equals(diff.kind())) {
                statements.add("CREATE " + (diff.detail() != null && diff.detail().startsWith("UNIQUE") ? "UNIQUE " : "")
                        + "INDEX " + SqlIdentifiers.quote(diff.name()) + " ON " + table
                        + " " + diff.detail());
            }
        }
        return statements;
    }

    /** 主键 + 校验和映射：只回传主键拼接键与 MD5，不回传整行 */
    private Map<String, String> checksums(Connection conn, SyncModels.TableRef ref, List<String> pk,
                                          List<String> valueColumns, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(String.join(", ", pk.stream().map(SqlIdentifiers::quote).toList()));
        if (valueColumns.isEmpty()) {
            sql.append(", ''");
        } else {
            sql.append(", MD5(CONCAT_WS(CHAR(31), ").append(String.join(", ", valueColumns.stream()
                    .map(col -> "COALESCE(CAST(" + SqlIdentifiers.quote(col) + " AS CHAR), '" + NULL_MARK + "')")
                    .toList())).append("))");
        }
        sql.append(" FROM ").append(qualified(ref)).append(" LIMIT ?");

        Map<String, String> result = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StringBuilder key = new StringBuilder();
                    for (int i = 1; i <= pk.size(); i++) {
                        if (i > 1) key.append(KEY_SEP);
                        String value = rs.getString(i);
                        key.append(value == null ? NULL_MARK : value);
                    }
                    result.put(key.toString(), rs.getString(pk.size() + 1));
                }
            }
        }
        return result;
    }

    // ===== 执行 =====

    private int applyDataChanges(Connection sourceConn, Connection targetConn,
                                 SyncCompareRequest request, List<String> pk, List<String> valueColumns,
                                 List<String> dataColumns, List<String> insertKeys,
                                 List<String> updateKeys, List<String> deleteKeys) throws SQLException {
        boolean autoCommit = targetConn.getAutoCommit();
        targetConn.setAutoCommit(false);
        try {
            int applied = 0;
            for (List<String> batch : chunks(insertKeys)) {
                for (Map<String, String> row : fetchRows(sourceConn, request.source(), dataColumns, pk, batch)) {
                    applied += insertRow(targetConn, request.target(), dataColumns, row);
                }
            }
            for (List<String> batch : chunks(updateKeys)) {
                for (Map<String, String> row : fetchRows(sourceConn, request.source(), dataColumns, pk, batch)) {
                    applied += updateRow(targetConn, request.target(), valueColumns, pk, row);
                }
            }
            for (String key : deleteKeys) {
                applied += deleteRow(targetConn, request.target(), pk, splitKey(key));
            }
            targetConn.commit();
            return applied;
        } catch (SQLException e) {
            rollbackQuietly(targetConn);
            log.warn("同步数据失败已回滚: {}", e.getMessage());
            throw new BizException(ErrorCode.SYNC_FAILED, message(e));
        } finally {
            try {
                targetConn.setAutoCommit(autoCommit);
            } catch (SQLException e) {
                log.debug("恢复 autoCommit 失败: {}", e.getMessage());
            }
        }
    }

    /** 按主键批量取源行：`WHERE (pk=?) OR (pk=?)`；返回列名→值（含 null） */
    private List<Map<String, String>> fetchRows(Connection conn, SyncModels.TableRef ref,
                                                List<String> columns, List<String> pk,
                                                List<String> keys) throws SQLException {
        if (keys.isEmpty() || columns.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(String.join(", ", columns.stream().map(SqlIdentifiers::quote).toList()));
        sql.append(" FROM ").append(qualified(ref)).append(" WHERE ");
        List<String> params = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("(");
            List<String> values = splitKey(keys.get(i));
            for (int c = 0; c < pk.size(); c++) {
                if (c > 0) sql.append(" AND ");
                sql.append(SqlIdentifiers.quote(pk.get(c))).append(" = ?");
                params.add(values.get(c));
            }
            sql.append(")");
        }

        List<Map<String, String>> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (String col : columns) {
                        row.put(col, rs.getString(col));
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private int insertRow(Connection conn, SyncModels.TableRef ref, List<String> columns,
                          Map<String, String> row) throws SQLException {
        String sql = "INSERT INTO " + qualified(ref)
                + " (" + String.join(", ", columns.stream().map(SqlIdentifiers::quote).toList()) + ")"
                + " VALUES (" + String.join(", ", columns.stream().map(c -> "?").toList()) + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindAll(ps, columns, row);
            return ps.executeUpdate();
        }
    }

    private int updateRow(Connection conn, SyncModels.TableRef ref, List<String> valueColumns,
                          List<String> pk, Map<String, String> row) throws SQLException {
        if (valueColumns.isEmpty()) {
            return 0;
        }
        String set = String.join(", ", valueColumns.stream()
                .map(col -> SqlIdentifiers.quote(col) + " = ?").toList());
        String where = String.join(" AND ", pk.stream()
                .map(col -> SqlIdentifiers.quote(col) + " = ?").toList());
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE " + qualified(ref) + " SET " + set + " WHERE " + where)) {
            int i = bindAll(ps, valueColumns, row);
            for (String col : pk) {
                ps.setString(i++, row.get(col));
            }
            return ps.executeUpdate();
        }
    }

    private int deleteRow(Connection conn, SyncModels.TableRef ref, List<String> pk,
                          List<String> values) throws SQLException {
        String where = String.join(" AND ", pk.stream()
                .map(col -> SqlIdentifiers.quote(col) + " = ?").toList());
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + qualified(ref) + " WHERE " + where)) {
            for (int i = 0; i < pk.size(); i++) {
                ps.setString(i + 1, values.get(i));
            }
            return ps.executeUpdate();
        }
    }

    private int bindAll(PreparedStatement ps, List<String> columns, Map<String, String> row)
            throws SQLException {
        int i = 1;
        for (String col : columns) {
            ps.setString(i++, row.get(col));
        }
        return i;
    }

    // ===== 工具 =====

    private void validate(SyncCompareRequest request) {
        if (request == null || request.source() == null || request.target() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "源表与目标表不能为空");
        }
    }

    private Connection connectionOf(SyncModels.TableRef ref) throws SQLException {
        return connectionService.requireDataSource(ref.connectionId()).getConnection();
    }

    /** 参与比对与同步的列：两侧都存在且非主键 */
    private List<String> valueColumns(List<TableColumn> sourceColumns, Set<String> targetNames,
                                      List<String> pk) {
        List<String> columns = new ArrayList<>();
        for (TableColumn col : sourceColumns) {
            if (!pk.contains(col.name()) && targetNames.contains(col.name())) {
                columns.add(col.name());
            }
        }
        return columns;
    }

    private Set<String> names(List<TableColumn> columns) {
        Set<String> result = new LinkedHashSet<>();
        for (TableColumn col : columns) {
            result.add(col.name());
        }
        return result;
    }

    private String typeOf(TableColumn col) {
        String type = col.dataType() == null ? "" : col.dataType().toLowerCase();
        String length = col.length() == null ? "" : "(" + col.length() + ")";
        return type + length + (col.nullable() ? " NULL" : " NOT NULL");
    }

    private String describe(TableIndex idx) {
        String columns = idx.columns() == null ? "" : String.join(", ", idx.columns());
        return (idx.unique() ? "UNIQUE " : "") + "(" + columns + ")";
    }

    private String qualified(SyncModels.TableRef ref) {
        return SqlIdentifiers.quote(ref.database()) + "." + SqlIdentifiers.quote(ref.table());
    }

    private SyncCompareResponse notComparable(String reason, List<ColumnDiff> columnDiffs,
                                              List<IndexDiff> indexDiffs, List<String> statements) {
        return new SyncCompareResponse(false, reason, columnDiffs, indexDiffs,
                new DataDiff(0, 0, 0, 0, false), statements);
    }

    private List<List<String>> chunks(List<String> keys) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < keys.size(); i += FETCH_BATCH) {
            batches.add(keys.subList(i, Math.min(i + FETCH_BATCH, keys.size())));
        }
        return batches;
    }

    private List<String> splitKey(String key) {
        List<String> values = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < key.length(); i++) {
            if (key.charAt(i) == 31) {
                values.add(key.substring(start, i));
                start = i + 1;
            }
        }
        values.add(key.substring(start));
        return values;
    }

    private void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException e) {
            log.warn("同步回滚失败: {}", e.getMessage());
        }
    }

    private String message(SQLException e) {
        return e.getMessage() == null ? "" : e.getMessage();
    }
}
