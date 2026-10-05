package com.github.lechandonga.ephemeralenvironmentorchestratorjava.error;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.domain.ErrorCode;

/**
 * 携带可区分 {@link ErrorCode} 的业务异常。
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
