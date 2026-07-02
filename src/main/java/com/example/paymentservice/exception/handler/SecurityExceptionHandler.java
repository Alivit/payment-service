package com.example.paymentservice.exception.handler;

import com.example.paymentservice.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         @NonNull HttpServletResponse response,
                         @NonNull AuthenticationException authException) throws IOException {
        log.warn("Security Filter: Unauthorized access attempt to {}", request.getRequestURI());
        createErrorResponse(response, HttpStatus.UNAUTHORIZED, "Invalid or missing access token");
    }

    @Override
    public void handle(HttpServletRequest request,
                       @NonNull HttpServletResponse response,
                       @NonNull AccessDeniedException accessDeniedException) throws IOException {
        log.warn("Security Filter: Access denied to {} - insufficient privileges", request.getRequestURI());
        createErrorResponse(response, HttpStatus.FORBIDDEN, "You do not have permission to perform this operation");
    }

    private void createErrorResponse(HttpServletResponse response, HttpStatus httpStatus, String customMessage) throws IOException {
        response.setStatus(httpStatus.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = ErrorResponse.of(
                httpStatus.value(),
                httpStatus.getReasonPhrase(),
                customMessage
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
