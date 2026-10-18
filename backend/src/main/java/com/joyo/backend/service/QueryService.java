package com.joyo.backend.service;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.QueryModels.ColumnMeta;
import com.joyo.backend.model.QueryModels.QueryExecuteRequest;
import com.joyo.backend.model.QueryModels.QueryExecuteResponse;
import com.joyo.backend.model.QueryModels.StatementResult;
import com.joyo.backend.util.SqlSplitter;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
/**
 * 查询执行（spec-03，rest-api.md 查询执行节）：
 * - 多语句按 SqlSplitter 拆分后依次执行；任一失败 → 中止后续并整体失败（v0.1 不返回部分结果）
 * - SELECT：setMaxRows(maxRows+1) 检测截断；全部值 getString 转字符串；结果入 LRU 缓存供翻页
 * - DML 返回影响行数；DDL 仅报执行成功
 * - 超时 → 3002；其余执行错误 → 3001（透传 MySQL 原始错误信息）
 */
@Service
public class QueryService {

    private static final Logger log = LoggerFactory.getLogger(QueryService.class);

    /** rest-api.md 约定：首屏分页大小 */
    private static final int FIRST_PAGE_SIZE = 100;

    private final ConnectionService connectionService;
    private final QueryResultCache resultCache;

    public QueryService(ConnectionService connectionService, QueryResultCache resultCache) {
        this.connectionService = connectionService;
        this.resultCache = resultCache;
    }

    public QueryExecuteResponse execute(String connectionId, QueryExecuteRequest request) {
        List<String> statements = SqlSplitter.split(request.sql());
        if (statements.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "没有可执行的语句");
        }

        HikariDataSource dataSource = connectionService.requireDataSource(connectionId);
        long batchStart = System.currentTimeMillis();
        List<StatementResult> results = new ArrayList<>(statements.size());

        // 整批使用同一连接：语义一致且减少建连开销
        try (Connection conn = dataSource.getConnection()) {
            int ordinal = 0;
            for (String sql : statements) {
                ordinal++;
                results.add(executeOne(conn, ordinal, sql, request));
            }
        } catch (BizException e) {
            throw e;
        } catch (SQLException e) {
            throw classify(e);
        }

        log.debug("批量执行完成: connectionId={}, 语句数={}, 耗时={}ms",
                connectionId, statements.size(), System.currentTimeMillis() - batchStart);
        return new QueryExecuteResponse(results, System.currentTimeMillis() - batchStart);
    }

    private StatementResult executeOne(
            Connection conn, int ordinal, String sql, QueryExecuteRequest request) throws SQLException {
        long start = System.currentTimeMillis();
        try (Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(request.queryTimeoutOrDefault());
            boolean hasResultSet = stmt.execute(sql);

            if (hasResultSet) {
                return executeSelect(stmt, ordinal, request, start);
            }
            long affected = stmt.getUpdateCount();
            String kind = affected >= 0 ? "UPDATE" : "DDL";
            log.debug("语句执行完成: ordinal={}, kind={}, affected={}", ordinal, kind, affected);
            return new StatementResult(ordinal, kind, null, null, null, null, null,
                    null, null, null, Math.max(affected, 0), System.currentTimeMillis() - start);
        } catch (SQLException e) {
            throw classify(e);
        }
    }

    private StatementResult executeSelect(
            Statement stmt, int ordinal, QueryExecuteRequest request, long start) throws SQLException {
        try (ResultSet rs = stmt.getResultSet()) {
            var meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            List<ColumnMeta> columns = new ArrayList<>(columnCount);
            Map<String, Integer> seenNames = new java.util.HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                // 重复列名自动加后缀（spec-04：JOIN 场景列头唯一）
                String label = meta.getColumnLabel(i);
                Integer seen = seenNames.get(label);
                if (seen != null) {
                    int suffix = seen;
                    while (seenNames.containsKey(label + "_" + suffix)) {
                        suffix++;
                    }
                    seenNames.put(label, seen + 1);
                    label = label + "_" + suffix;
                }
                seenNames.put(label, 1);
                columns.add(new ColumnMeta(label, meta.getColumnTypeName(i),
                        meta.isNullable(i) != java.sql.ResultSetMetaData.columnNoNulls));
            }

            // 多拉一行用于检测截断；全部值转字符串（精度红线）
            int limit = request.maxRowsOrDefault();
            List<String[]> rows = new ArrayList<>();
            boolean truncated = false;
            while (rs.next()) {
                if (rows.size() >= limit) {
                    truncated = true;
                    break;
                }
                String[] row = new String[columnCount];
                for (int i = 1; i <= columnCount; i++) {
                    row[i - 1] = rs.getString(i);
                }
                rows.add(row);
            }

            String queryId = resultCache.put(rows, !truncated, rows.size());
            int pageSize = Math.min(limit, FIRST_PAGE_SIZE);
            List<String[]> firstPage = rows.subList(0, Math.min(pageSize, rows.size()));
            return new StatementResult(ordinal, "SELECT", queryId, columns,
                    !truncated, (long) rows.size(), truncated, firstPage, 1, pageSize,
                    null, System.currentTimeMillis() - start);
        }
    }

    /** 查询错误分类：超时 → 3002；其余 → 3001（透传 MySQL 原始信息） */
    static BizException classify(SQLException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (e instanceof SQLTimeoutException
                || message.toLowerCase().contains("timed out")
                || message.toLowerCase().contains("query execution was interrupted")) {
            return new BizException(ErrorCode.QUERY_TIMEOUT, ErrorCode.QUERY_TIMEOUT.getMessage());
        }
        return new BizException(ErrorCode.QUERY_ERROR, message);
    }
}
