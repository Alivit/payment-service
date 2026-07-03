package com.minispring.paymentservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.hibernate.validator.constraints.CreditCardNumber;

public record PaymentProcessRequest(
        @NotNull(message = "Order ID is required") UUID orderId,

        @NotBlank(message = "Card number is required")
        @CreditCardNumber(ignoreNonDigitCharacters = true, message = "Invalid credit card format")
        String cardNumber,

        @NotBlank(message = "CVV is required") @Pattern(regexp = "^[0-9]{3,4}$", message = "CVV must be 3 or 4 digits")
        String cvv) {
    public PaymentProcessRequest {
        if (cardNumber != null) {
            cardNumber = cardNumber.replaceAll("\\D", "");
        }
        if (cvv != null) {
            cvv = cvv.trim();
        }
    }
}
