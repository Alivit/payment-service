package com.minispring.paymentservice.controller;

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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentService paymentService;

    @GetMapping("/users")
    public ResponseEntity<Page<PaymentView>> getPaymentsByUserId(@RequestParam UUID userId, Pageable pageable) {
        return ResponseEntity.ok(paymentService.getByUserId(userId, pageable));
    }

    @GetMapping("/orders")
    public ResponseEntity<PaymentView> getPaymentByOrderId(@RequestParam UUID orderId) {
        return ResponseEntity.ok(paymentService.getByOrderId(orderId));
    }

    @GetMapping("/status")
    public ResponseEntity<Page<PaymentView>> getPaymentsByStatus(
            @RequestParam PaymentStatus status, Pageable pageable) {
        return ResponseEntity.ok(paymentService.getByStatus(status, pageable));
    }

    @GetMapping("/total-sum")
    public ResponseEntity<PaymentTotalSum> getTotalSumForAll(@Valid PaymentSearchCriteria request) {
        return ResponseEntity.ok(paymentService.getTotalSum(request));
    }

    @GetMapping("/total-sum/by-user")
    public ResponseEntity<PaymentTotalSum> getTotalSumForUser(
            @RequestParam UUID userId, @Valid PaymentSearchCriteria request) {
        return ResponseEntity.ok(paymentService.getTotalSumByUserId(userId, request));
    }
}
