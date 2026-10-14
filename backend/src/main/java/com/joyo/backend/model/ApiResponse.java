package com.joyo.backend.model;

/**
 * 统一响应包装（docs/90-conventions/api-conventions.md 第 3 节）：
 * 统一 HTTP 200 + 业务码，code=0 表示成功。
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
