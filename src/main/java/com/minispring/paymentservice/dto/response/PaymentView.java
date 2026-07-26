package com.minispring.paymentservice.dto.response;

import com.minispring.paymentservice.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentView(
        UUID id, UUID orderId, UUID userId, BigDecimal amount, PaymentStatus status, Instant createdAt) {}
