package com.minispring.paymentservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderView(UUID orderId, UUID userId, BigDecimal totalPrice) {}
