package com.example.paymentservice.service.impl;

import com.example.paymentservice.client.PaymentGatewayClient;
import com.example.paymentservice.dto.PaymentCreateDto;
import com.example.paymentservice.dto.PaymentFilterRequestDto;
import com.example.paymentservice.dto.PaymentResponseDto;
import com.example.paymentservice.dto.TotalAmountDto;
import com.example.paymentservice.exception.ResourceNotFoundException;
import com.example.paymentservice.mapper.PaymentMapper;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.example.paymentservice.exception.ExceptionAnswer.PAYMENT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentGatewayClient paymentGatewayClient;

    @Override
    public PaymentResponseDto create(PaymentCreateDto request) {
        Optional<Payment> lastPaymentOpt = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(request.orderId());
        if (lastPaymentOpt.isPresent() && lastPaymentOpt.get().getPaymentStatus() == PaymentStatus.SUCCESS) {
            return paymentMapper.paymentToPaymentResponseDto(lastPaymentOpt.get());
        }
        Payment newPayment = paymentMapper.paymentCreateDtoToPayment(request);
        newPayment.setPaymentStatus(PaymentStatus.PENDING);
        newPayment = paymentRepository.save(newPayment);
        PaymentStatus finalStatus = paymentGatewayClient.processPayment(newPayment);
        newPayment.setPaymentStatus(finalStatus);
        return paymentMapper.paymentToPaymentResponseDto(paymentRepository.save(newPayment));
    }

    @Override
    public PaymentResponseDto getLastByOrderId(UUID orderId) {
        return paymentMapper.paymentToPaymentResponseDto(getExistsLastPaymentByOrderId(orderId));
    }

    @Override
    public List<PaymentResponseDto> getByOrderId(UUID orderId, boolean desc) {
        Sort sort = desc ? Sort.by(Sort.Direction.DESC, "createdAt")
                : Sort.by(Sort.Direction.ASC, "createdAt");
        return paymentRepository.findByOrderId(orderId, sort)
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

    @Transactional
    @Override
    public TotalAmountDto getTotalAmountByUserId(UUID userId, PaymentFilterRequestDto request) {
        return paymentRepository.sumPaymentsByUserIdAndDateRange(userId, request)
                .orElseGet(() -> new TotalAmountDto(BigDecimal.ZERO));
    }

    @Transactional
    @Override
    public TotalAmountDto getTotalAmount(PaymentFilterRequestDto request) {
        return paymentRepository.sumAllPaymentsByDateRange(request)
                .orElseGet(() -> new TotalAmountDto(BigDecimal.ZERO));
    }

    private Payment getExistsLastPaymentByOrderId(UUID orderId) {
        return paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(PAYMENT_NOT_FOUND, orderId)));
    }
}
