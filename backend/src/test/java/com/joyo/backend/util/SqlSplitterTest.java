package com.joyo.backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SqlSplitterTest {

    private static List<String> split(String sql) {
        return SqlSplitter.split(sql);
    }

    @Test
    @DisplayName("单语句无分号也能产出")
    void 单语句无分号() {
        assertThat(split("SELECT 1")).containsExactly("SELECT 1");
    }

    @Test
    @DisplayName("两条简单语句按分号拆分")
    void 两条语句() {
        assertThat(split("SELECT 1; SELECT 2;"))
                .containsExactly("SELECT 1", "SELECT 2");
    }

    @Test
    @DisplayName("字符串中的分号不拆分")
    void 字符串分号() {
        assertThat(split("SELECT 'a;b' AS v; SELECT 2"))
                .containsExactly("SELECT 'a;b' AS v", "SELECT 2");
    }

    @Test
    @DisplayName("单引号双写转义不结束字符串")
    void 双写转义() {
        assertThat(split("SELECT 'it''s; ok'; SELECT 2"))
                .containsExactly("SELECT 'it''s; ok'", "SELECT 2");
    }

    @Test
    @DisplayName("反斜杠转义的引号不结束字符串")
    void 反斜杠转义() {
        assertThat(split("SELECT 'a\\'; SELECT 1; -- x' ; SELECT 2"))
                .containsExactly("SELECT 'a\\'; SELECT 1; -- x'", "SELECT 2");
    }

    @Test
    @DisplayName("反引号标识符中的分号不拆分；反引号内反斜杠不转义")
    void 反引号() {
        assertThat(split("SELECT * FROM `we;ird\\\\table`; SELECT 1"))
                .containsExactly("SELECT * FROM `we;ird\\\\table`", "SELECT 1");
    }

    @Test
    @DisplayName("双引号字符串中的分号不拆分")
    void 双引号() {
        assertThat(split("SELECT \"a;b\"; SELECT 2"))
                .containsExactly("SELECT \"a;b\"", "SELECT 2");
    }

    @Test
    @DisplayName("-- 行注释中的分号不拆分")
    void 行注释() {
        assertThat(split("SELECT 1; -- comment; still comment\nSELECT 2"))
                .containsExactly("SELECT 1", "SELECT 2");
    }

    @Test
    @DisplayName("# 行注释中的分号不拆分")
    void 井号注释() {
        assertThat(split("SELECT 1; # c; c\nSELECT 2"))
                .containsExactly("SELECT 1", "SELECT 2");
    }

    @Test
    @DisplayName("块注释中的分号不拆分，注释本体被剔除")
    void 块注释() {
        assertThat(split("SELECT /* a;b;c */ 1; SELECT 2"))
                .containsExactly("SELECT  1", "SELECT 2");
    }

    @Test
    @DisplayName("-- 后无空白不是注释（按减号处理），分号正常拆分（MySQL 规则）")
    void 双横线非注释() {
        assertThat(split("SELECT 1--x;y"))
                .containsExactly("SELECT 1--x", "y");
    }

    @Test
    @DisplayName("纯注释输入产出空列表")
    void 纯注释() {
        assertThat(split("-- only comment\n/* block */")).isEmpty();
        assertThat(split("  \n\t ; ; ")).isEmpty();
    }

    @Test
    @DisplayName("语句前后空白被裁剪；注释被剔除")
    void 裁剪与剔除() {
        assertThat(split("  SELECT 1  -- tail\n; SELECT 2 /* x */ ;"))
                .containsExactly("SELECT 1", "SELECT 2");
    }

    @Test
    @DisplayName("多语句中第二条失败场景的拆分正确性（三语句）")
    void 三语句() {
        assertThat(split("UPDATE t SET a=1; SELECT 'x;y'; DELETE FROM t;"))
                .containsExactly("UPDATE t SET a=1", "SELECT 'x;y'", "DELETE FROM t");
    }

    @Test
    @DisplayName("null 与空输入产出空列表")
    void 空输入() {
        assertThat(split(null)).isEmpty();
        assertThat(split("")).isEmpty();
    }

    @Test
    @DisplayName("CRLF 换行正常处理")
    void crlf() {
        assertThat(split("SELECT 1;\r\n-- c\r\nSELECT 2"))
                .containsExactly("SELECT 1", "SELECT 2");
    }
}
