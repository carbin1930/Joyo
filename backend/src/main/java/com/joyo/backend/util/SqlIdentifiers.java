package com.joyo.backend.util;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import java.nio.charset.StandardCharsets;

/**
 * SQL 标识符工具（coding-backend.md 第 4 节）：
 * SHOW CREATE TABLE 等语句无法参数绑定标识符，必须统一经本工具转义并校验。
 */
public final class SqlIdentifiers {

    private SqlIdentifiers() {
    }

    /**
     * 合法性校验：拒绝空名、含 NUL/换行等控制字符的名称。
     * 反引号允许出现（由 quote 负责双写转义）。
     */
    public static String validate(String name) {
        if (name == null || name.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "标识符不能为空");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == 0 || c == '\n' || c == '\r') {
                throw new BizException(ErrorCode.PARAM_INVALID, "标识符含非法字符");
            }
        }
        // 拒绝无法在 utf8mb4 下安全编码的序列
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(name)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "标识符含非法字符");
        }
        return name;
    }

    /** 反引号包裹 + 反引号双写转义：a`b → `a``b` */
    public static String quote(String name) {
        validate(name);
        return "`" + name.replace("`", "``") + "`";
    }
}
