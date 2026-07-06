package com.minispring.paymentservice.controller;

import com.minispring.paymentservice.dto.PaymentFilterRequestDto;
import com.minispring.paymentservice.dto.PaymentResponseDto;
import com.minispring.paymentservice.dto.TotalAmountDto;
import com.minispring.paymentservice.model.PaymentStatus;
import com.minispring.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentService paymentService;

    @GetMapping("/users/{userId}")
    public ResponseEntity<Page<PaymentResponseDto>> getPaymentsByUserId(
            @PathVariable UUID userId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(paymentService.getByUserId(userId, pageable));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<List<PaymentResponseDto>> getPaymentsByOrderId(
            @PathVariable UUID orderId,
            @RequestParam(required = false, defaultValue = "false") boolean desc
    ) {
        return ResponseEntity.ok(paymentService.getByOrderId(orderId, desc));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<Page<PaymentResponseDto>> getPaymentsByStatus(
            @PathVariable PaymentStatus status,
            Pageable pageable
    ) {
        return ResponseEntity.ok(paymentService.getByStatus(status, pageable));
    }

    @GetMapping("/total-sum")
    public ResponseEntity<TotalAmountDto> getTotalSumForAll(
            PaymentFilterRequestDto request
    ) {
        return ResponseEntity.ok(paymentService.getTotalAmount(request));
    }

    @GetMapping("/total-sum/by-user")
    public ResponseEntity<TotalAmountDto> getTotalSumForUser(
            @RequestParam UUID userId,
            PaymentFilterRequestDto request
    ) {
        return ResponseEntity.ok(paymentService.getTotalAmountByUserId(userId, request));
    }
}
