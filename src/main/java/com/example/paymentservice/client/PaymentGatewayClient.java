package com.example.paymentservice.client;

import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;

public interface PaymentGatewayClient {
    PaymentStatus processPayment(Payment payment);
}
