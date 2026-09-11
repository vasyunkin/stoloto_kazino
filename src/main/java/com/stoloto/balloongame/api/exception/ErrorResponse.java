package com.stoloto.balloongame.api.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;

/**
 * Uniform error body for all API errors.
 * Format: { "code", "message", "timestamp", "details"? }
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final String code;
    private final String message;
    private final Instant timestamp;
    private final String details;

    public ErrorResponse(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ErrorResponse(ErrorCode code, String message, String details) {
        this.code = code.name();
        this.message = message;
        this.timestamp = Instant.now();
        this.details = details;
    }
}
