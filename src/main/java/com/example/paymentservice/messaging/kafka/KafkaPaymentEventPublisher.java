package com.example.paymentservice.messaging.kafka;

import com.example.paymentservice.dto.PaymentEventDto;
import com.example.paymentservice.messaging.PaymentEventPublisher;
import com.example.paymentservice.model.OutboxEvent;
import com.example.paymentservice.model.OutboxStatus;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.repository.OutboxRepository;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    @SneakyThrows
    @Override
    public void publishPayment(Payment payment) {
        PaymentEventDto event = new PaymentEventDto(
                payment.getId(),
                payment.getOrderId(),
                payment.getPaymentStatus().name()
        );

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(payment.getOrderId().toString())
                .eventType("PAYMENT_PROCESSED")
                .payload(jsonMapper.writeValueAsString(event))
                .status(OutboxStatus.PENDING)
                .build();

        outboxRepository.save(outboxEvent);
    }
}
