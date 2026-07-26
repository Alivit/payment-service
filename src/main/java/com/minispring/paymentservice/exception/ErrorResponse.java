package com.minispring.paymentservice.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        String traceId,
        Instant timestamp,
        List<String> details) {
    public static ErrorResponse of(int status, String error, String message, String path, String traceId) {
        return new ErrorResponse(status, error, message, path, traceId, Instant.now(), null);
    }

    public static ErrorResponse of(
            int status, String error, String message, String path, String traceId, List<String> details) {
        return new ErrorResponse(status, error, message, path, traceId, Instant.now(), details);
    }
}
