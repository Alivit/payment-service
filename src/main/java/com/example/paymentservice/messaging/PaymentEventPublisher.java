package com.example.paymentservice.messaging;

import com.example.paymentservice.model.Payment;

public interface PaymentEventPublisher {
    void publishPayment(Payment payment);
}
