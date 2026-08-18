package com.happytrade.market.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Error payload. {@code retryAfter} is present only when the client should try again after a
 * specific number of seconds.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Integer retryAfter) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }
}
