package com.joyo.backend.exception;

/**
 * 业务错误码（docs/30-api/rest-api.md 第 5 节错误码表）。
 * 新增错误码必须：落在正确分段 → 先登记 rest-api.md → 提供用户可读中文文案。
 */
public enum ErrorCode {

    /** 参数校验失败 */
    PARAM_INVALID(1001, "参数校验失败"),
    /** 资源不存在（连接会话/queryId 等） */
    NOT_FOUND(1002, "资源不存在"),
    /** X-Joyo-Token 校验失败（Electron Main 与后端不一致或缺失） */
    TOKEN_INVALID(1003, "应用鉴权失败"),
    /** 后端内部错误 */
    INTERNAL_ERROR(1500, "发生内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
