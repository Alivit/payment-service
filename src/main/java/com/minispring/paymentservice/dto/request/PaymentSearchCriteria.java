package com.minispring.paymentservice.dto.request;

import com.minispring.paymentservice.util.validation.ValidDateRange;
import java.time.Instant;

@ValidDateRange
public record PaymentSearchCriteria(Instant from, Instant to) {
    public PaymentSearchCriteria {
        if (from == null) from = Instant.EPOCH;
        if (to == null) to = Instant.now();
    }
}
