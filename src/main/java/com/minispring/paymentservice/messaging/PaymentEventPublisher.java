package com.minispring.paymentservice.messaging;

import com.minispring.paymentservice.model.Payment;

public interface PaymentEventPublisher {
    void publishPayment(Payment payment);
}
