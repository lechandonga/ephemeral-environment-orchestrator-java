package com.github.lechandonga.ephemeralenvironmentorchestratorjava.domain;

import org.springframework.http.HttpStatus;

/**
 * 业务失败原因码。每种可预期的拒绝都有独立、可区分的编码。
 */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    ENV_QUOTA_EXCEEDED(HttpStatus.CONFLICT),
    ENV_PROVISION_CONFLICT(HttpStatus.CONFLICT),
    ENV_NOT_FOUND(HttpStatus.NOT_FOUND),
    TEAM_MISMATCH(HttpStatus.FORBIDDEN),
    ENV_EXPIRED(HttpStatus.CONFLICT),
    ENV_RELEASING(HttpStatus.CONFLICT),
    ENV_RECLAIMED(HttpStatus.CONFLICT),
    ILLEGAL_TRANSITION(HttpStatus.CONFLICT);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
