package com.minispring.paymentservice.messaging.kafka;

import com.minispring.paymentservice.mapper.PaymentMapper;
import com.minispring.paymentservice.messaging.PaymentEventPublisher;
import com.minispring.paymentservice.messaging.event.PaymentCreateEvent;
import com.minispring.paymentservice.model.OutboxEvent;
import com.minispring.paymentservice.model.OutboxStatus;
import com.minispring.paymentservice.model.Payment;
import com.minispring.paymentservice.repository.OutboxRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final PaymentMapper paymentMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishPayment(Payment payment) {
        try {
            PaymentCreateEvent event = paymentMapper.toEvent(payment);

            Map<String, Object> payloadMap = jsonMapper.convertValue(event, new TypeReference<>() {});

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateId(payment.getOrderId().toString())
                    .eventType("CREATE_PAYMENT")
                    .payload(payloadMap)
                    .status(OutboxStatus.PENDING)
                    .build();

            outboxRepository.save(outboxEvent);
            log.debug("Outbox event created for Payment OrderId: {}", payment.getOrderId());

        } catch (IllegalArgumentException e) {
            log.error("Failed to serialize Outbox event payload for OrderId: {}", payment.getOrderId(), e);
            throw new IllegalStateException("Could not create outbox event for payment processing", e);
        }
    }
}
