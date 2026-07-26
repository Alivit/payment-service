package com.minispring.paymentservice.client.impl;

import com.minispring.paymentservice.client.PaymentGatewayClient;
import com.minispring.paymentservice.dto.request.PaymentProcessRequest;
import com.minispring.paymentservice.model.Payment;
import com.minispring.paymentservice.model.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor;

@Slf4j
@Component
public class PaymentGatewayAdapter implements PaymentGatewayClient {

    private final RestClient restClient;

    public PaymentGatewayAdapter(
            @Value("${app.external-api.url:https://www.random.org}") String baseUrl,
            LogbookClientHttpRequestInterceptor logbookInterceptor) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .requestInterceptor(logbookInterceptor)
                .build();
    }

    @Override
    public PaymentStatus processPayment(Payment payment, PaymentProcessRequest requestDto) {
        log.info("Initiating external payment request for Payment ID: {}", payment.getId());

        String maskedCard = maskCardNumber(requestDto.cardNumber());

        log.info(
                "Initiating payment request for Order: {}, Amount: {}, Card: {}",
                payment.getOrderId(),
                payment.getPaymentAmount(),
                maskedCard);

        try {
            String response = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/integers/")
                            .queryParam("num", 1)
                            .queryParam("min", 1)
                            .queryParam("max", 100)
                            .queryParam("col", 1)
                            .queryParam("base", 10)
                            .queryParam("format", "plain")
                            .queryParam("rnd", "new")
                            .build())
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(response)) {
                log.warn("External API returned empty response for Payment ID: {}", payment.getId());
                return PaymentStatus.FAILED;
            }

            int randomNumber = Integer.parseInt(response.trim());
            log.info("External API returned number: {} for Payment ID: {}", randomNumber, payment.getId());

            return (randomNumber % 2 == 0) ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;

        } catch (RestClientException | NumberFormatException e) {
            log.error(
                    "Failed to communicate with external payment API for Payment ID {}: {}",
                    payment.getId(),
                    e.getMessage());
            return PaymentStatus.FAILED;
        }
    }

    private String maskCardNumber(String cardNumber) {
        if (!StringUtils.hasText(cardNumber) || cardNumber.length() < 4) {
            return "****-****-****-XXXX";
        }
        return "****-****-****-" + cardNumber.substring(cardNumber.length() - 4);
    }
}
