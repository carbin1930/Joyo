package com.joyo.backend.service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * Excel(.xlsx) 导出 Writer（spec-15 第 3 节）：
 * SXSSF 滑动窗口（100 行）流式写，单元格一律字符串（NULL 空单元格），
 * 日期保持 JDBC 字符串原样（与结果网格一致）。
 */
final class XlsxWriter implements ExportWriter {

    private final SXSSFWorkbook workbook;
    private final SXSSFSheet sheet;
    private final OutputStream out;
    private int nextRow;

    XlsxWriter(OutputStream out) {
        this.out = out;
        this.workbook = new SXSSFWorkbook(100);
        this.sheet = workbook.createSheet("data");
    }

    @Override
    public void writeHeader(List<String> columns) throws IOException {
        Row row = sheet.createRow(nextRow++);
        for (int i = 0; i < columns.size(); i++) {
            row.createCell(i).setCellValue(columns.get(i));
        }
    }

    @Override
    public void writeRow(String[] row) throws IOException {
        Row xlsxRow = sheet.createRow(nextRow++);
        for (int i = 0; i < row.length; i++) {
            if (row[i] != null) {
                xlsxRow.createCell(i).setCellValue(row[i]);
            }
        }
    }

    @Override
    public void close() throws IOException {
        try {
            workbook.write(out);
            out.flush();
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }
}