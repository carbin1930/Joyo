package com.joyo.backend.service;

import java.io.IOException;
import java.util.List;

/**
 * 导出格式 Writer 统一接口（spec-15 第 3 节）。
 * 实现只 flush 不关闭底层响应流。
 */
interface ExportWriter extends AutoCloseable {

    void writeHeader(List<String> columns) throws IOException;

    void writeRow(String[] row) throws IOException;

    @Override
    void close() throws IOException;
}