package com.minispring.paymentservice.messaging.event;

import java.util.UUID;

public record PaymentCreateEvent(UUID paymentId, UUID orderId, String status) {}
