package com.minispring.paymentservice.service.impl;

import static com.minispring.paymentservice.exception.ExceptionAnswer.ORDER_NOT_FOUND;

import com.minispring.paymentservice.client.OrderGrpcClient;
import com.minispring.paymentservice.client.PaymentGatewayClient;
import com.minispring.paymentservice.dto.request.PaymentProcessRequest;
import com.minispring.paymentservice.dto.request.PaymentSearchCriteria;
import com.minispring.paymentservice.dto.response.OrderView;
import com.minispring.paymentservice.dto.response.PaymentTotalSum;
import com.minispring.paymentservice.dto.response.PaymentView;
import com.minispring.paymentservice.exception.ResourceNotFoundException;
import com.minispring.paymentservice.mapper.PaymentMapper;
import com.minispring.paymentservice.messaging.PaymentEventPublisher;
import com.minispring.paymentservice.model.Payment;
import com.minispring.paymentservice.model.PaymentStatus;
import com.minispring.paymentservice.repository.PaymentRepository;
import com.minispring.paymentservice.service.PaymentService;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentGatewayClient paymentGatewayClient;
    private final PaymentEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final OrderGrpcClient orderGrpcClient;

    @Override
    public PaymentView create(UUID userId, PaymentProcessRequest request) {
        log.info("Initiating payment process for OrderId: {}, requested by UserId: {}", request.orderId(), userId);
        Payment paymentToProcess = paymentRepository
                .findByOrderId(request.orderId())
                .orElseGet(() -> {
                    log.info(
                            "No existing payment intent found for OrderId: {}. Fetching order details via gRPC...",
                            request.orderId());

                    OrderView orderView = orderGrpcClient.getOrderPriceById(request.orderId());

                    if (!orderView.userId().equals(userId)) {
                        log.warn(
                                "SECURITY ALERT: User {} attempted to pay for Order {} owned by User {}",
                                userId,
                                request.orderId(),
                                orderView.userId());
                        throw new AccessDeniedException("You do not have permission to pay for this order.");
                    }

                    Payment newPayment = paymentMapper.toNewPayment(orderView);

                    return paymentRepository.save(newPayment);
                });

        if (!paymentToProcess.getUserId().equals(userId)) {
            log.warn(
                    "SECURITY ALERT: User {} attempted to access payment for Order {} owned by User {}",
                    userId,
                    request.orderId(),
                    paymentToProcess.getUserId());
            throw new AccessDeniedException("You do not have permission to access payments for this order.");
        }

        if (paymentToProcess.getPaymentStatus() == PaymentStatus.SUCCESS) {
            log.info("Order {} is already paid. Returning existing successful payment status.", request.orderId());
            return paymentMapper.toView(paymentToProcess);
        }

        log.debug(
                "Calling external gateway for OrderId: {} with amount: {}",
                request.orderId(),
                paymentToProcess.getPaymentAmount());
        PaymentStatus finalStatus = paymentGatewayClient.processPayment(paymentToProcess, request);
        log.info("External gateway responded with status: {} for OrderId: {}", finalStatus, request.orderId());

        return transactionTemplate.execute(_ -> {
            paymentToProcess.setPaymentStatus(finalStatus);
            paymentRepository.save(paymentToProcess);
            eventPublisher.publishPayment(paymentToProcess);
            return paymentMapper.toView(paymentToProcess);
        });
    }

    @Override
    public PaymentView getByOrderId(UUID orderId) {
        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ORDER_NOT_FOUND, orderId)));
        return paymentMapper.toView(payment);
    }

    @Override
    public PaymentView getByOrderIdAndUserId(UUID orderId, UUID userId) {
        if (paymentRepository.existsByOrderIdAndUserIdNot(orderId, userId)) {
            log.warn("User {} attempted to access payment for Order {} belonging to someone else!", userId, orderId);
            throw new AccessDeniedException("You do not have permission to perform this operation.");
        }

        Payment payment = paymentRepository
                .findByOrderIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ORDER_NOT_FOUND, orderId)));

        return paymentMapper.toView(payment);
    }

    @Override
    public Page<PaymentView> getByUserId(UUID userId, Pageable pageable) {
        return paymentRepository.findByUserId(userId, pageable).map(paymentMapper::toView);
    }

    @Override
    public Page<PaymentView> getByStatus(PaymentStatus status, Pageable pageable) {
        return paymentRepository.findByPaymentStatus(status, pageable).map(paymentMapper::toView);
    }

    @Override
    public Page<PaymentView> getByUserIdAndStatus(UUID userId, PaymentStatus status, Pageable pageable) {
        return paymentRepository
                .findByUserIdAndPaymentStatus(userId, status, pageable)
                .map(paymentMapper::toView);
    }

    @Override
    public PaymentTotalSum getTotalSumByUserId(UUID userId, PaymentSearchCriteria request) {
        return paymentRepository
                .sumPaymentsByUserIdAndDateRange(userId, request)
                .orElseGet(() -> new PaymentTotalSum(BigDecimal.ZERO));
    }

    @Override
    public PaymentTotalSum getTotalSum(PaymentSearchCriteria request) {
        return paymentRepository
                .sumAllPaymentsByDateRange(request)
                .orElseGet(() -> new PaymentTotalSum(BigDecimal.ZERO));
    }
}
