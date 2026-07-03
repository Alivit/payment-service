package com.minispring.paymentservice.dto;

import java.util.UUID;

public record PaymentEventDto(
        UUID paymentId,
        UUID orderId,
        String status
) {
}
