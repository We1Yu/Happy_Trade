package com.happytrade.market.web;

import com.happytrade.market.provider.UpstreamException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps parameter and upstream failures onto the API's error payload. */
@RestControllerAdvice
public class MarketExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleInvalidParameter(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_PARAMETER", e.getMessage()));
    }

    @ExceptionHandler(UpstreamException.RateLimited.class)
    public ResponseEntity<ApiError> handleRateLimited(UpstreamException.RateLimited e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("UPSTREAM_RATE_LIMITED", e.getMessage(), e.retryAfterSeconds()));
    }

    @ExceptionHandler(UpstreamException.Blocked.class)
    public ResponseEntity<ApiError> handleBlocked(UpstreamException.Blocked e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of("UPSTREAM_BLOCKED", e.getMessage()));
    }

    @ExceptionHandler(UpstreamException.Timeout.class)
    public ResponseEntity<ApiError> handleTimeout(UpstreamException.Timeout e) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ApiError.of("UPSTREAM_TIMEOUT", e.getMessage()));
    }
}
