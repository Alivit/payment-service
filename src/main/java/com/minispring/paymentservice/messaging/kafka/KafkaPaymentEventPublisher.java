package com.minispring.paymentservice.messaging.kafka;

import com.minispring.paymentservice.dto.PaymentEventDto;
import com.minispring.paymentservice.messaging.PaymentEventPublisher;
import com.minispring.paymentservice.model.OutboxEvent;
import com.minispring.paymentservice.model.OutboxStatus;
import com.minispring.paymentservice.model.Payment;
import com.minispring.paymentservice.repository.OutboxRepository;
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
