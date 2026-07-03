package com.minispring.paymentservice.exception.handler;

import com.minispring.paymentservice.exception.ResourceNotFoundException;
import io.grpc.Status;
import io.grpc.StatusException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GlobalGrpcExceptionHandler implements GrpcExceptionHandler {

    @Override
    public @Nullable StatusException handleException(@NonNull Throwable exception) {

        return switch (exception) {
            case ResourceNotFoundException resourceNotFoundException -> {
                log.warn("gRPC Resource not found: {}", resourceNotFoundException.getMessage());
                yield Status.NOT_FOUND
                        .withDescription(resourceNotFoundException.getMessage())
                        .asException();
            }
            case IllegalArgumentException illegalArgumentException -> {
                log.warn("gRPC Invalid argument format: {}", illegalArgumentException.getMessage());
                yield Status.INVALID_ARGUMENT
                        .withDescription("Invalid UUID or argument format")
                        .asException();
            }
            case AuthenticationException authenticationException -> {
                log.warn("gRPC Authentication failed: {}", authenticationException.getMessage());
                yield Status.UNAUTHENTICATED
                        .withDescription("Invalid or missing token")
                        .asException();
            }
            case AccessDeniedException accessDeniedException -> {
                log.warn("gRPC Access denied: {}", accessDeniedException.getMessage());
                yield Status.PERMISSION_DENIED
                        .withDescription("Insufficient privileges")
                        .asException();
            }
            default -> null;
        };
    }
}
