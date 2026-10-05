package com.github.lechandonga.ephemeralenvironmentorchestratorjava.web;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.error.BusinessException;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.error.ErrorCode;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.service.Clock;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.service.SystemClock;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Clock clock = new SystemClock();

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<EnvDtos.ErrorResponse> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.getHttpStatus())
                .body(new EnvDtos.ErrorResponse(code.name(), ex.getMessage(), clock.now()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<EnvDtos.ErrorResponse> handleUnreadable(Exception ex) {
        return badRequest("请求体无法解析：" + ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<EnvDtos.ErrorResponse> handleOther(Exception ex) {
        return badRequest(ex.getMessage());
    }

    private ResponseEntity<EnvDtos.ErrorResponse> badRequest(String message) {
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getHttpStatus())
                .body(new EnvDtos.ErrorResponse(
                        ErrorCode.INVALID_REQUEST.name(), message, clock.now()));
    }
}
