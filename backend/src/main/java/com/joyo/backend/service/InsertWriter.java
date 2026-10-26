package com.joyo.backend.service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * SQL INSERT 导出 Writer（spec-15 第 3 节）：
 * 逐行 INSERT INTO `t` (`c1`,...) VALUES (...);
 * 字符串按 MySQL 默认模式转义（\ 与 '），数值类型裸值，NULL → NULL。
 */
final class InsertWriter implements ExportWriter {

    /** 按数值裸值输出的列类型（MySQL 类型名，大写） */
    private static final Set<String> NUMERIC_TYPES = Set.of(
            "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT",
            "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE", "BIT");

    private final Writer out;
    private final String table;
    private final List<String> types;
    private List<String> columns = List.of();

    InsertWriter(OutputStream out, String table, List<String> types) {
        this.out = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        this.table = table;
        this.types = types;
    }

    @Override
    public void writeHeader(List<String> columns) throws IOException {
        this.columns = columns;
        out.write("SET NAMES utf8mb4;\r\n");
    }

    @Override
    public void writeRow(String[] row) throws IOException {
        StringBuilder sb = new StringBuilder("INSERT INTO `").append(table).append("` (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('`').append(columns.get(i)).append('`');
        }
        sb.append(") VALUES (");
        for (int i = 0; i < row.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(sqlValue(row[i], types.get(i)));
        }
        sb.append(");\r\n");
        out.write(sb.toString());
    }

    @Override
    public void close() throws IOException {
        out.flush();
    }

    private String sqlValue(String value, String type) {
        if (value == null) {
            return "NULL";
        }
        if (type != null && NUMERIC_TYPES.contains(type.toUpperCase())) {
            return value;
        }
        // 先翻倍反斜杠再双写单引号（MySQL 默认模式下两者都是转义符）
        return '\'' + value.replace("\\", "\\\\").replace("'", "''") + '\'';
    }
}