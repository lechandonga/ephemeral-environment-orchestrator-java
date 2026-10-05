package com.github.lechandonga.ephemeralenvironmentorchestratorjava.error;

/**
 * 携带明确 {@link ErrorCode} 的业务异常，由全局异常处理器转成统一响应。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
