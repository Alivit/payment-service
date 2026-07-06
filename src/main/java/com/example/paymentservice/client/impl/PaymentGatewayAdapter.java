package com.example.paymentservice.client.impl;

import com.example.paymentservice.client.PaymentGatewayClient;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class PaymentGatewayAdapter implements PaymentGatewayClient {

    private final RestClient restClient;

    public PaymentGatewayAdapter() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://www.random.org")
                .build();
    }

    @Override
    public PaymentStatus processPayment(Payment payment) {
        log.info("Initiating external payment request for Payment ID: {}", payment.getId());

        try {
            String response = restClient.get()
                    .uri("/integers/?num=1&min=1&max=100&col=1&base=10&format=plain&rnd=new")
                    .retrieve()
                    .body(String.class);
            if (response == null || response.isBlank()) {
                log.warn("External API returned empty response");
                return PaymentStatus.FAILED;
            }
            int randomNumber = Integer.parseInt(response.trim());
            log.info("External API returned number: {} for Payment ID: {}", randomNumber, payment.getId());
            return (randomNumber % 2 == 0) ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;

        } catch (RestClientException | NumberFormatException e) {
            log.error("Failed to communicate with external payment API: {}", e.getMessage());
            return PaymentStatus.FAILED;
        }
    }
}
