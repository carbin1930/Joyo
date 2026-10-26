package com.joyo.backend.service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV 导出 Writer（spec-15 第 3 节）：
 * UTF-8 带 BOM（Excel 直开中文不乱码）；RFC 4180 转义；NULL → 空串。
 * 只 flush 不关闭底层流（响应流由容器管理）。
 */
final class CsvWriter implements ExportWriter {

    private final Writer out;
    private boolean headerWritten;

    CsvWriter(OutputStream out) {
        this.out = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }

    @Override
    public void writeHeader(List<String> columns) throws IOException {
        if (!headerWritten) {
            out.write('\uFEFF');
            headerWritten = true;
        }
        out.write(join(columns));
        out.write("\r\n");
    }

    @Override
    public void writeRow(String[] row) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(row[i]));
        }
        out.write(sb.toString());
        out.write("\r\n");
    }

    @Override
    public void close() throws IOException {
        out.flush();
    }

    private String join(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(values.get(i)));
        }
        return sb.toString();
    }

    /** RFC 4180：含逗号/引号/换行的字段用双引号包裹，内部引号翻倍；NULL → 空串 */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
