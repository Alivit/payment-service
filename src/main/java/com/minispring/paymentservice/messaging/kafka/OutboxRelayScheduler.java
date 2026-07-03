package com.minispring.paymentservice.messaging.kafka;

import com.minispring.paymentservice.model.OutboxEvent;
import com.minispring.paymentservice.model.OutboxStatus;
import com.minispring.paymentservice.repository.OutboxRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    @Value("${app.kafka.topics.payment-events}")
    private String PAYMENT_TOPIC;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findByStatus(
                OutboxStatus.PENDING, PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "createdAt")));

        for (OutboxEvent event : pendingEvents) {
            Message<Map<String, Object>> message = MessageBuilder.withPayload(event.getPayload())
                    .setHeader(KafkaHeaders.TOPIC, PAYMENT_TOPIC)
                    .setHeader(KafkaHeaders.KEY, event.getAggregateId())
                    .setHeader("__TypeId__", "com.minispring.orderservice.messaging.event.PaymentCreatedEvent")
                    .setHeader("eventType", event.getEventType())
                    .build();

            kafkaTemplate.send(message).whenComplete((_, ex) -> {
                if (ex == null) {
                    event.setStatus(OutboxStatus.COMPLETED);
                    outboxRepository.save(event);
                    log.info("Outbox event {} sent to Kafka", event.getId());
                } else {
                    log.error(
                            "Failed to send Outbox event {}. Will retry on next schedule. Error: {}",
                            event.getId(),
                            ex.getMessage());
                }
            });
        }
    }
}
