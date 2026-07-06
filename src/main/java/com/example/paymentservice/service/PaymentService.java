package com.example.paymentservice.service;

import com.example.paymentservice.dto.PaymentCreateDto;
import com.example.paymentservice.dto.PaymentFilterRequestDto;
import com.example.paymentservice.dto.PaymentResponseDto;
import com.example.paymentservice.dto.TotalAmountDto;
import com.example.paymentservice.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PaymentService {

    PaymentResponseDto create(PaymentCreateDto request);

    PaymentResponseDto getLastByOrderId(UUID orderId);

    List<PaymentResponseDto> getByOrderId(UUID orderId, boolean desc);

    Page<PaymentResponseDto> getByUserId(UUID userId, Pageable pageable);

    Page<PaymentResponseDto> getByStatus(PaymentStatus status, Pageable pageable);

    TotalAmountDto getTotalAmountByUserId(UUID userId, PaymentFilterRequestDto request);

    TotalAmountDto getTotalAmount(PaymentFilterRequestDto request);
}
