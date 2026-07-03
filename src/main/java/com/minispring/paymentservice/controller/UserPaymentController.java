package com.minispring.paymentservice.controller;

import com.minispring.paymentservice.dto.request.PaymentProcessRequest;
import com.minispring.paymentservice.dto.request.PaymentSearchCriteria;
import com.minispring.paymentservice.dto.response.PaymentTotalSum;
import com.minispring.paymentservice.dto.response.PaymentView;
import com.minispring.paymentservice.model.PaymentStatus;
import com.minispring.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/payments")
@RequiredArgsConstructor
public class UserPaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentView> create(
            @Valid @RequestBody PaymentProcessRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.create(userId, request));
    }

    @GetMapping
    public ResponseEntity<Page<PaymentView>> getPaymentsByUserId(@AuthenticationPrincipal Jwt jwt, Pageable pageable) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(paymentService.getByUserId(userId, pageable));
    }

    @GetMapping("/status")
    public ResponseEntity<Page<PaymentView>> getMyPayments(
            @RequestParam PaymentStatus status, Pageable pageable, @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(paymentService.getByUserIdAndStatus(userId, status, pageable));
    }

    @GetMapping("/orders")
    public ResponseEntity<PaymentView> getPaymentByOrderId(
            @RequestParam UUID orderId, @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(paymentService.getByOrderIdAndUserId(orderId, userId));
    }

    @GetMapping("/total-sum")
    public ResponseEntity<PaymentTotalSum> getMyTotalSum(
            @Valid PaymentSearchCriteria request, @AuthenticationPrincipal Jwt jwt) {
        UUID currentUserId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(paymentService.getTotalSumByUserId(currentUserId, request));
    }
}
