package com.minispring.paymentservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCreateDto(
        @NotNull(message = "Order ID must not be blank")
        UUID orderId,

        @NotNull(message = "User ID must not be blank")
        UUID userId,

        @NotNull(message = "Amount must not be null")
        @Positive(message = "Amount must be greater than zero")
        BigDecimal amount
) {
}
