package com.minispring.paymentservice.controller;

import com.minispring.paymentservice.dto.PaymentCreateDto;
import com.minispring.paymentservice.dto.PaymentFilterRequestDto;
import com.minispring.paymentservice.dto.PaymentResponseDto;
import com.minispring.paymentservice.dto.TotalAmountDto;
import com.minispring.paymentservice.model.PaymentStatus;
import com.minispring.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/payments")
@RequiredArgsConstructor
public class UserPaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponseDto> create(@Valid @RequestBody PaymentCreateDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.create(request));
    }

    @GetMapping
    public ResponseEntity<Page<PaymentResponseDto>> getPaymentsByUserId(
            @AuthenticationPrincipal Jwt jwt,
            Pageable pageable
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(paymentService.getByUserId(userId, pageable));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<Page<PaymentResponseDto>> getMyPayments(
            @PathVariable PaymentStatus status,
            Pageable pageable,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(paymentService.getByUserIdAndStatus(userId, status, pageable));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<List<PaymentResponseDto>> getPaymentsByOrderId(
            @PathVariable UUID orderId,
            @RequestParam(required = false, defaultValue = "false") boolean desc,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(paymentService.getByOrderIdAndUserId(orderId, userId, desc));
    }

    @GetMapping("/total-sum")
    public ResponseEntity<TotalAmountDto> getMyTotalSum(PaymentFilterRequestDto request,
                                                        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID currentUserId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(paymentService.getTotalAmountByUserId(currentUserId, request));
    }
}
