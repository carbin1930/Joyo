package com.joyo.backend.service;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.ExportRequest;
import com.joyo.backend.util.SqlIdentifiers;
import org.springframework.stereotype.Service;

/**
 * 导出服务（spec-15 第 3 节）：MySQL 流式游标（fetchSize=Integer.MIN_VALUE）
 * 逐行写三种格式，大结果集内存平稳。开流前的 SQL/连接错误走 BizException
 * → GlobalExceptionHandler 输出 ApiResponse JSON；开流后错误表现为流中断，
 * 由客户端负责清理 .part 临时文件。
 */
@Service
public class ExportService {

    private final ConnectionService connectionService;

    public ExportService(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    public void write(ExportRequest request, OutputStream out) throws IOException {
        var dataSource = connectionService.requireDataSource(request.connectionId());
        try (Connection conn = dataSource.getConnection()) {
            if (request.database() != null && !request.database().isBlank()) {
                try (Statement use = conn.createStatement()) {
                    use.execute("USE " + SqlIdentifiers.quote(request.database()));
                }
            }
            // MySQL 流式读取约定：FORWARD_ONLY + fetchSize=Integer.MIN_VALUE
            try (Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                stmt.setFetchSize(Integer.MIN_VALUE);
                try (ResultSet rs = stmt.executeQuery(request.sql())) {
                    writeResultSet(request, rs, out);
                }
            }
        } catch (SQLException e) {
            throw new BizException(ErrorCode.QUERY_ERROR, e.getMessage());
        }
    }

    private void writeResultSet(ExportRequest request, ResultSet rs, OutputStream out)
            throws SQLException, IOException {
        ResultSetMetaData md = rs.getMetaData();
        int columnCount = md.getColumnCount();
        List<String> columns = new ArrayList<>(columnCount);
        List<String> types = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            columns.add(md.getColumnLabel(i));
            types.add(md.getColumnTypeName(i));
        }

        try (ExportWriter writer = createWriter(request, out, types)) {
            writer.writeHeader(columns);
            int limit = request.rowLimit() == null ? Integer.MAX_VALUE : request.rowLimit();
            String[] row = new String[columnCount];
            for (int count = 0; count < limit && rs.next(); count++) {
                for (int i = 0; i < columnCount; i++) {
                    row[i] = rs.getString(i + 1);
                }
                writer.writeRow(row);
            }
        }
    }

    private ExportWriter createWriter(ExportRequest request, OutputStream out, List<String> types) {
        return switch (request.format()) {
            case "csv" -> new CsvWriter(out);
            case "insert" -> new InsertWriter(out, request.tableName(), types);
            default -> new XlsxWriter(out);
        };
    }
}