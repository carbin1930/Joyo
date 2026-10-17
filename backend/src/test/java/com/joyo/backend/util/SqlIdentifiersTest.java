package com.joyo.backend.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joyo.backend.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SqlIdentifiersTest {

    @Test
    @DisplayName("quote：普通名称用反引号包裹")
    void 普通名称() {
        assertThat(SqlIdentifiers.quote("user")).isEqualTo("`user`");
    }

    @Test
    @DisplayName("quote：名称中的反引号双写转义")
    void 反引号转义() {
        assertThat(SqlIdentifiers.quote("a`b")).isEqualTo("`a``b`");
    }

    @Test
    @DisplayName("quote：中文与空格名称合法")
    void 中文与空格() {
        assertThat(SqlIdentifiers.quote("订单 item")).isEqualTo("`订单 item`");
    }

    @Test
    @DisplayName("空名称抛参数异常")
    void 空名称() {
        assertThatThrownBy(() -> SqlIdentifiers.quote(""))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> SqlIdentifiers.quote(null))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("含控制字符的名称抛参数异常")
    void 控制字符() {
        assertThatThrownBy(() -> SqlIdentifiers.quote("a\nb"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> SqlIdentifiers.quote("a\u0000b"))
                .isInstanceOf(BizException.class);
    }
}
