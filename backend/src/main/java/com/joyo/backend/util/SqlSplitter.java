package com.joyo.backend.util;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 语句拆分器（spec-03，rest-api.md 查询执行节）：
 * 按分号拆分多语句，正确跳过——
 * 单引号字符串（含 \\' 与 '' 转义）、双引号字符串、反引号标识符、
 * -- 行注释、# 行注释、块注释。
 * 拆分后剔除注释，空白/纯注释块不产出语句。
 */
public final class SqlSplitter {

    private SqlSplitter() {
    }

    public static List<String> split(String sql) {
        List<String> statements = new ArrayList<>();
        if (sql == null || sql.isEmpty()) {
            return statements;
        }
        StringBuilder current = new StringBuilder();
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            char next = i + 1 < n ? sql.charAt(i + 1) : '\0';

            // 行注释：-- 后必须跟空白/行尾（MySQL 规则）；# 直接行注释
            if ((c == '-' && next == '-' && (i + 2 >= n || isWhitespace(sql.charAt(i + 2))))
                    || c == '#') {
                i = skipLineComment(sql, i);
                continue;
            }
            // 块注释
            if (c == '/' && next == '*') {
                i = skipBlockComment(sql, i);
                continue;
            }
            // 字符串与标识符：内部所有字符原样保留，分号不算分隔
            char close = quoteClose(c);
            if (close != 0) {
                current.append(c);
                i++;
                i = copyQuoted(sql, i, close, current);
                continue;
            }
            // 分号：语句边界
            if (c == ';') {
                addIfMeaningful(statements, current);
                i++;
                continue;
            }
            current.append(c);
            i++;
        }
        addIfMeaningful(statements, current);
        return statements;
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    /** 返回引号的闭合字符：' " `；其余返回 0 */
    private static char quoteClose(char c) {
        return switch (c) {
            case '\'', '"', '`' -> c;
            default -> 0;
        };
    }

    /**
     * 从已 append 的开引号之后开始，复制到闭合引号为止。
     * 处理反斜杠转义与双写转义（'' `` ""）。
     */
    private static int copyQuoted(String sql, int i, char close, StringBuilder out) {
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            out.append(c);
            if (c == '\\' && close != '`') {
                // 反斜杠转义：跳过下一字符（MySQL 字符串内规则；反引号内无反斜杠转义）
                if (i + 1 < n) {
                    out.append(sql.charAt(i + 1));
                    i += 2;
                    continue;
                }
                i++;
                continue;
            }
            if (c == close) {
                // 双写转义：'' `` ""，仍在引号内
                if (i + 1 < n && sql.charAt(i + 1) == close) {
                    out.append(close);
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return i; // 未闭合（容错：到结尾）
    }

    private static int skipLineComment(String sql, int i) {
        int n = sql.length();
        while (i < n && sql.charAt(i) != '\n') {
            i++;
        }
        return i; // 换行符留给主循环
    }

    private static int skipBlockComment(String sql, int i) {
        int end = sql.indexOf("*/", i + 2);
        return end < 0 ? sql.length() : end + 2;
    }

    /** 去掉注释后若仅剩空白则丢弃该块 */
    private static void addIfMeaningful(List<String> statements, StringBuilder chunk) {
        String text = chunk.toString();
        chunk.setLength(0);
        if (!text.isBlank()) {
            statements.add(text.trim());
        }
    }
}
