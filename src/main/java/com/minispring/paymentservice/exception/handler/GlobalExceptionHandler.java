package com.minispring.paymentservice.exception.handler;

import com.minispring.paymentservice.exception.ErrorResponse;
import com.minispring.paymentservice.exception.ResourceNotFoundException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TRACE_ID_KEY = "traceId";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                getTraceId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            OptimisticLockingFailureException ex, HttpServletRequest request) {

        log.warn(
                "Optimistic locking failure (concurrent update) on [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "CONCURRENT_MODIFICATION",
                "The resource was modified by another request. Please refresh the data and try again.",
                request.getRequestURI(),
                getTraceId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKeyException(
            DuplicateKeyException ex, HttpServletRequest request) {

        log.warn("Duplicate key violation on [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "DUPLICATE_ENTITY",
                "A record with such unique parameters already exists.",
                request.getRequestURI(),
                getTraceId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> String.format("Field '%s': %s", error.getField(), error.getDefaultMessage()))
                .toList();

        log.warn("Validation error on [{}]: {}", request.getRequestURI(), details);

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                "Request parameters failed validation",
                request.getRequestURI(),
                getTraceId(),
                details);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String parameterName = ex.getName();
        Object rejectedValue = ex.getValue();
        Class<?> requiredType = ex.getRequiredType();

        log.warn(
                "Type mismatch on [{}]. Param: '{}', Rejected value: '{}', Expected type: {}",
                request.getRequestURI(),
                parameterName,
                rejectedValue,
                requiredType != null ? requiredType.getSimpleName() : "unknown");

        String message = String.format("Invalid parameter format '%s'.", parameterName);
        List<String> details = null;

        if (requiredType != null) {
            if (requiredType.isEnum()) {
                message = String.format("Invalid value '%s' for the parameter '%s'.", rejectedValue, parameterName);
                details = Arrays.stream(requiredType.getEnumConstants())
                        .map(Object::toString)
                        .toList();
            } else if (requiredType.equals(java.util.UUID.class)) {
                message = String.format("The value '%s' is not a valid UUID.", rejectedValue);
                details = List.of("Expected format: 123e4567-e89b-12d3-a456-426614174000");
            }
        }

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_PARAMETER_FORMAT",
                message,
                request.getRequestURI(),
                getTraceId(),
                details);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParams(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        log.warn("Missing parameter on [{}]: {}", request.getRequestURI(), ex.getParameterName());

        String message = String.format("The required parameter '%s' is missing.", ex.getParameterName());
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "MISSING_PARAMETER", message, request.getRequestURI(), getTraceId());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex, HttpServletRequest request) {

        log.warn("Illegal state on [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "ILLEGAL_STATE_ERROR",
                ex.getMessage(),
                request.getRequestURI(),
                getTraceId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {

        log.warn("Invalid argument on [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_ARGUMENT",
                ex.getMessage(),
                request.getRequestURI(),
                getTraceId());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.warn("Malformed JSON request on [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "MALFORMED_JSON_REQUEST",
                "The request body is missing or contains invalid JSON structure.",
                request.getRequestURI(),
                getTraceId());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Business logic access denied on [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(), "FORBIDDEN", ex.getMessage(), request.getRequestURI(), getTraceId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<ErrorResponse> handleGrpcException(StatusRuntimeException ex, HttpServletRequest request) {
        Status status = ex.getStatus();
        log.warn(
                "gRPC exception propagated to HTTP on [{}]. gRPC Status: {}, Description: {}",
                request.getRequestURI(),
                status.getCode(),
                status.getDescription());

        HttpStatus httpStatus =
                switch (status.getCode()) {
                    case NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
                    case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
                    case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
                    case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
                    case ABORTED -> HttpStatus.CONFLICT;
                    default -> HttpStatus.INTERNAL_SERVER_ERROR;
                };

        String message = status.getDescription() != null ? status.getDescription() : "Downstream service error";

        ErrorResponse response = ErrorResponse.of(
                httpStatus.value(), status.getCode().name(), message, request.getRequestURI(), getTraceId());

        return ResponseEntity.status(httpStatus).body(response);
    }

    private String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }
}
