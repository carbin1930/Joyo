package com.joyo.backend.exception;

/**
 * 业务异常：service 层唯一允许抛出的异常类型，
 * 由 GlobalExceptionHandler 统一转换为 ApiResponse。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
