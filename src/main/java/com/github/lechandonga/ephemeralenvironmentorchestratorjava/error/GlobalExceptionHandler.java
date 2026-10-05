package com.github.lechandonga.ephemeralenvironmentorchestratorjava.error;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.domain.ErrorCode;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 把业务异常统一翻译成带 errorCode 的 JSON 响应，便于调用方区分失败原因。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "errorCode", code.name(),
                "message", ex.getMessage());
        return ResponseEntity.status(code.getHttpStatus()).body(body);
    }
}
