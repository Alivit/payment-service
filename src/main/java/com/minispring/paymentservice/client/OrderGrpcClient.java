package com.minispring.paymentservice.client;

import com.minispring.grpc.order.GetOrderPriceByIdRequest;
import com.minispring.grpc.order.OrderGrpcServiceGrpc;
import com.minispring.paymentservice.dto.response.OrderView;
import com.minispring.paymentservice.exception.ResourceNotFoundException;
import com.minispring.paymentservice.mapper.PaymentMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderGrpcClient {

    private final OrderGrpcServiceGrpc.OrderGrpcServiceBlockingStub orderGrpc;
    private final PaymentMapper paymentMapper;

    @Value("${app.grpc.timeout.seconds}")
    private long grpcTimeoutSeconds;

    @CircuitBreaker(name = "orderServiceGrpc", fallbackMethod = "getOrderPriceFallback")
    public OrderView getOrderPriceById(UUID orderId) {
        log.debug("Get order price from order service for orderId={}", orderId);

        GetOrderPriceByIdRequest request = GetOrderPriceByIdRequest.newBuilder()
                .setOrderId(orderId.toString())
                .build();

        return executeGrpcCall(
                () -> paymentMapper.toOrderPriceDetails(orderGrpc
                        .withDeadlineAfter(grpcTimeoutSeconds, TimeUnit.SECONDS)
                        .getOrderPriceById(request)
                        .getOrderPrice()),
                String.format("Order with id %s not found", orderId));
    }

    private <T> T executeGrpcCall(Supplier<T> grpcCall, String notFoundMessage) {
        try {
            return grpcCall.get();
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException(notFoundMessage);
            }
            log.error(
                    "gRPC call failed with status: {}. Reason: {}",
                    ex.getStatus().getCode(),
                    ex.getMessage());
            throw ex;
        }
    }

    private OrderView getOrderPriceFallback(Throwable ex) {
        if (ex instanceof ResourceNotFoundException || ex instanceof StatusRuntimeException) {
            throw (RuntimeException) ex;
        }
        log.warn("Order service unavailable for reason={}", ex.getMessage());
        throw new IllegalStateException("Order service is currently unavailable", ex);
    }
}
