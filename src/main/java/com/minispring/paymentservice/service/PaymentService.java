package com.minispring.paymentservice.service;

import com.minispring.paymentservice.dto.PaymentCreateDto;
import com.minispring.paymentservice.dto.PaymentFilterRequestDto;
import com.minispring.paymentservice.dto.PaymentResponseDto;
import com.minispring.paymentservice.dto.TotalAmountDto;
import com.minispring.paymentservice.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PaymentService {

    PaymentResponseDto create(PaymentCreateDto request);

    List<PaymentResponseDto> getByOrderId(UUID orderId, boolean desc);

    List<PaymentResponseDto> getByOrderIdAndUserId(UUID orderId, UUID userId, boolean desc);

    Page<PaymentResponseDto> getByUserId(UUID userId, Pageable pageable);

    Page<PaymentResponseDto> getByStatus(PaymentStatus status, Pageable pageable);

    Page<PaymentResponseDto> getByUserIdAndStatus(UUID userId, PaymentStatus status, Pageable pageable);

    TotalAmountDto getTotalAmountByUserId(UUID userId, PaymentFilterRequestDto request);

    TotalAmountDto getTotalAmount(PaymentFilterRequestDto request);
}
