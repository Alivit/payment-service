package com.minispring.paymentservice.util.validation;

import com.minispring.paymentservice.dto.request.PaymentSearchCriteria;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Instant;

public class ValidDateRangeValidator implements ConstraintValidator<ValidDateRange, PaymentSearchCriteria> {

    @Override
    public boolean isValid(
            PaymentSearchCriteria paymentSearchCriteria, ConstraintValidatorContext constraintValidatorContext) {
        if (paymentSearchCriteria == null) {
            return true;
        }

        Instant from = paymentSearchCriteria.from() != null ? paymentSearchCriteria.from() : Instant.EPOCH;
        Instant to = paymentSearchCriteria.to() != null ? paymentSearchCriteria.to() : Instant.now();

        return !from.isAfter(to);
    }
}
