package com.joyo.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * 三种导出 Writer 单元测试（spec-15 第 3 节格式细则）。
 */
class ExportWriterTest {

    private static String asText(ByteArrayOutputStream out) {
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void csv带BOM转义与NULL空串() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CsvWriter writer = new CsvWriter(out)) {
            writer.writeHeader(List.of("id", "name"));
            writer.writeRow(new String[] { "1", "a,b" });
            writer.writeRow(new String[] { null, "say \"hi\"\nline" });
        }
        String text = asText(out);
        assertTrue(text.startsWith("\uFEFF"));
        assertTrue(text.contains("id,name\r\n"));
        assertTrue(text.contains("1,\"a,b\"\r\n"));
        assertTrue(text.contains("\"say \"\"hi\"\""));
    }

    @Test
    void csv普通值不加引号() {
        assertEquals("plain", CsvWriter.escape("plain"));
        assertEquals("", CsvWriter.escape(null));
        assertEquals("\"a\"\"b\"", CsvWriter.escape("a\"b"));
    }

    @Test
    void insert转义与数值裸值() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InsertWriter writer = new InsertWriter(out, "users", List.of("INT", "VARCHAR", "DECIMAL"))) {
            writer.writeHeader(List.of("id", "name", "score"));
            writer.writeRow(new String[] { "1", "O'Brien\\path", null });
            writer.writeRow(new String[] { "2", "n", "88.5" });
        }
        String text = asText(out);
        assertTrue(text.startsWith("SET NAMES utf8mb4;"));
        assertTrue(text.contains("INSERT INTO `users` (`id`,`name`,`score`) VALUES (1,'O''Brien\\\\path',NULL);"));
        assertTrue(text.contains("VALUES (2,'n',88.5);"));
    }

    @Test
    void xlsx写出可读回() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XlsxWriter writer = new XlsxWriter(out)) {
            writer.writeHeader(List.of("id", "name"));
            writer.writeRow(new String[] { "1", null });
        }
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Row header = wb.getSheetAt(0).getRow(0);
            assertEquals("id", header.getCell(0).getStringCellValue());
            Row data = wb.getSheetAt(0).getRow(1);
            assertEquals("1", data.getCell(0).getStringCellValue());
            assertEquals(null, data.getCell(1));
        }
    }
}