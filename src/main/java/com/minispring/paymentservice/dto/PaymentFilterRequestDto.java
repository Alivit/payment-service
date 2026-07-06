package com.minispring.paymentservice.dto;

import java.time.Instant;

public record PaymentFilterRequestDto(
        Instant from,
        Instant to
) {
    public PaymentFilterRequestDto {
        if (from == null) from = Instant.EPOCH;
        if (to == null) to = Instant.now();
    }
}
