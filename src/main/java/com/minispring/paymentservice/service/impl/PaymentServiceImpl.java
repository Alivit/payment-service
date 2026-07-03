package com.minispring.paymentservice.service.impl;

import com.minispring.paymentservice.client.PaymentGatewayClient;
import com.minispring.paymentservice.dto.PaymentCreateDto;
import com.minispring.paymentservice.dto.PaymentFilterRequestDto;
import com.minispring.paymentservice.dto.PaymentResponseDto;
import com.minispring.paymentservice.dto.TotalAmountDto;
import com.minispring.paymentservice.mapper.PaymentMapper;
import com.minispring.paymentservice.messaging.PaymentEventPublisher;
import com.minispring.paymentservice.model.Payment;
import com.minispring.paymentservice.model.PaymentStatus;
import com.minispring.paymentservice.repository.PaymentRepository;
import com.minispring.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentGatewayClient paymentGatewayClient;
    private final PaymentEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    @Override
    public PaymentResponseDto create(PaymentCreateDto request) {
        Optional<Payment> lastPaymentOpt = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(request.orderId());
        if (lastPaymentOpt.isPresent() && lastPaymentOpt.get().getPaymentStatus() == PaymentStatus.SUCCESS) {
            return paymentMapper.paymentToPaymentResponseDto(lastPaymentOpt.get());
        }
        Payment payment = paymentMapper.paymentCreateDtoToPayment(request);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        final Payment savedPayment = paymentRepository.save(payment);
        PaymentStatus finalStatus = paymentGatewayClient.processPayment(savedPayment);

        return transactionTemplate.execute(status -> {
            savedPayment.setPaymentStatus(finalStatus);
            paymentRepository.save(savedPayment);
            eventPublisher.publishPayment(savedPayment);
            return paymentMapper.paymentToPaymentResponseDto(savedPayment);
        });
    }

    @Override
    public List<PaymentResponseDto> getByOrderId(UUID orderId, boolean desc) {
        Sort sort = desc ? Sort.by(Sort.Direction.DESC, "createdAt") : Sort.by(Sort.Direction.ASC, "createdAt");
        return paymentRepository.findByOrderId(orderId, sort)
                .stream().map(paymentMapper::paymentToPaymentResponseDto).toList();
    }

    @Override
    public List<PaymentResponseDto> getByOrderIdAndUserId(UUID orderId, UUID userId, boolean desc) {
        if (paymentRepository.existsByOrderIdAndUserIdNot(orderId, userId)) {
            log.warn("User {} attempted to access payments for Order {} belonging to someone else!", userId, orderId);
            throw new AccessDeniedException("You do not have permission to access payments for this order.");
        }
        Sort sort = desc ? Sort.by(Sort.Direction.DESC, "createdAt") : Sort.by(Sort.Direction.ASC, "createdAt");
        return paymentRepository.findByOrderIdAndUserId(orderId, userId, sort)
                .stream().map(paymentMapper::paymentToPaymentResponseDto).toList();
    }

    @Override
    public Page<PaymentResponseDto> getByUserId(UUID userId, Pageable pageable) {
        return paymentRepository.findByUserId(userId, pageable).map(paymentMapper::paymentToPaymentResponseDto);
    }

    @Override
    public Page<PaymentResponseDto> getByStatus(PaymentStatus status, Pageable pageable) {
        return paymentRepository.findByPaymentStatus(status, pageable).map(paymentMapper::paymentToPaymentResponseDto);
    }

    @Override
    public Page<PaymentResponseDto> getByUserIdAndStatus(UUID userId, PaymentStatus status, Pageable pageable) {
        return paymentRepository.findByUserIdAndPaymentStatus(userId, status, pageable)
                .map(paymentMapper::paymentToPaymentResponseDto);
    }

    @Override
    public TotalAmountDto getTotalAmountByUserId(UUID userId, PaymentFilterRequestDto request) {
        return paymentRepository.sumPaymentsByUserIdAndDateRange(userId, request)
                .orElseGet(() -> new TotalAmountDto(BigDecimal.ZERO));
    }

    @Override
    public TotalAmountDto getTotalAmount(PaymentFilterRequestDto request) {
        return paymentRepository.sumAllPaymentsByDateRange(request)
                .orElseGet(() -> new TotalAmountDto(BigDecimal.ZERO));
    }
}
