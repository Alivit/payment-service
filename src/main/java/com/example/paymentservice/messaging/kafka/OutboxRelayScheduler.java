package com.example.paymentservice.messaging.kafka;

import com.example.paymentservice.model.OutboxEvent;
import com.example.paymentservice.model.OutboxStatus;
import com.example.paymentservice.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    @Value("${app.kafka.topics.payment-events}")
    private String PAYMENT_TOPIC;
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findByStatus(
                OutboxStatus.PENDING,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "createdAt"))
        );

        for (OutboxEvent event : pendingEvents) {
            try {
                kafkaTemplate.send(PAYMENT_TOPIC, event.getAggregateId(), event.getPayload()).get();

                event.setStatus(OutboxStatus.COMPLETED);
                outboxRepository.save(event);
                log.info("Outbox event {} sent to Kafka", event.getId());

            } catch (Exception e) {
                log.error("Failed to send Outbox event {}. Will retry. Error: {}", event.getId(), e.getMessage());
                break;
            }
        }
    }

}
