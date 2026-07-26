package com.minispring.paymentservice.exception.handler;

import com.minispring.paymentservice.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String TRACE_ID_KEY = "traceId";
    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AuthenticationException authException)
            throws IOException {
        log.warn(
                "Security Filter: Unauthorized access attempt to {} - {}",
                request.getRequestURI(),
                authException.getMessage());
        createErrorResponse(request, response, HttpStatus.UNAUTHORIZED, "Invalid or missing access token");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AccessDeniedException accessDeniedException)
            throws IOException {

        log.warn("Security Filter: Access denied to {} - Insufficient privileges", request.getRequestURI());
        createErrorResponse(
                request, response, HttpStatus.FORBIDDEN, "You do not have permission to perform this operation");
    }

    private void createErrorResponse(
            HttpServletRequest request, HttpServletResponse response, HttpStatus httpStatus, String customMessage)
            throws IOException {

        response.setStatus(httpStatus.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String traceId = MDC.get(TRACE_ID_KEY);

        ErrorResponse errorResponse = ErrorResponse.of(
                httpStatus.value(), httpStatus.getReasonPhrase(), customMessage, request.getRequestURI(), traceId);

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
