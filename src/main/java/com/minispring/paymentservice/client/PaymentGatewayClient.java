package com.minispring.paymentservice.client;

import com.minispring.paymentservice.model.Payment;
import com.minispring.paymentservice.model.PaymentStatus;

public interface PaymentGatewayClient {
    PaymentStatus processPayment(Payment payment);
}
