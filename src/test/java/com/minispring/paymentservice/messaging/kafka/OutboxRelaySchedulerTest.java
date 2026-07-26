package com.minispring.paymentservice.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minispring.paymentservice.model.OutboxEvent;
import com.minispring.paymentservice.model.OutboxStatus;
import com.minispring.paymentservice.repository.OutboxRepository;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    private static final String TOPIC_NAME = "test-payment-events";

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OutboxRelayScheduler scheduler;

    @Captor
    private ArgumentCaptor<OutboxEvent> eventCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "PAYMENT_TOPIC", TOPIC_NAME);
    }

    private OutboxEvent createPendingEvent(String aggregateId) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(aggregateId)
                .payload(java.util.Map.of("status", "TEST"))
                .status(OutboxStatus.PENDING)
                .build();
    }

    @Test
    void shouldDoNothingWhenNoPendingEvents() {
        when(outboxRepository.findByStatus(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());

        scheduler.processOutboxEvents();

        verify(kafkaTemplate, never()).send(any(Message.class));
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void shouldProcessAndCompleteSingleEventSuccessfully() {
        OutboxEvent event = createPendingEvent("order-1");

        when(outboxRepository.findByStatus(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(event));

        when(kafkaTemplate.send(any(Message.class))).thenReturn(CompletableFuture.completedFuture(null));

        scheduler.processOutboxEvents();

        verify(outboxRepository).save(eventCaptor.capture());
        OutboxEvent savedEvent = eventCaptor.getValue();

        assertThat(savedEvent.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
    }

    @Test
    void shouldProcessMultipleEventsSuccessfully() {
        OutboxEvent event1 = createPendingEvent("order-1");
        OutboxEvent event2 = createPendingEvent("order-2");

        when(outboxRepository.findByStatus(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(event1, event2));

        when(kafkaTemplate.send(any(Message.class))).thenReturn(CompletableFuture.completedFuture(null));

        scheduler.processOutboxEvents();

        verify(outboxRepository, times(2)).save(eventCaptor.capture());

        List<OutboxEvent> savedEvents = eventCaptor.getAllValues();
        assertThat(savedEvents).hasSize(2).allMatch(e -> e.getStatus() == OutboxStatus.COMPLETED);
    }

    @Test
    void shouldNotSaveWhenKafkaFutureFails() {
        OutboxEvent event = createPendingEvent("order-1");

        when(outboxRepository.findByStatus(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka connection refused"));

        when(kafkaTemplate.send(any(Message.class))).thenReturn(failedFuture);

        scheduler.processOutboxEvents();

        verify(outboxRepository, never()).save(any());
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void shouldHandleMixedResultsGracefullyWithoutBreakingLoop() {
        OutboxEvent event1 = createPendingEvent("order-1"); // Успешно
        OutboxEvent event2 = createPendingEvent("order-2"); // Ошибка
        OutboxEvent event3 = createPendingEvent("order-3"); // Успешно

        when(outboxRepository.findByStatus(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(event1, event2, event3));

        CompletableFuture<SendResult<String, Object>> successFuture = CompletableFuture.completedFuture(null);

        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka timeout"));

        when(kafkaTemplate.send(any(Message.class)))
                .thenReturn(successFuture)
                .thenReturn(failedFuture)
                .thenReturn(successFuture);

        scheduler.processOutboxEvents();

        verify(kafkaTemplate, times(3)).send(any(Message.class));

        verify(outboxRepository, times(2)).save(eventCaptor.capture());

        List<OutboxEvent> savedEvents = eventCaptor.getAllValues();
        assertThat(savedEvents).extracting(OutboxEvent::getAggregateId).containsExactlyInAnyOrder("order-1", "order-3");

        assertThat(event2.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }
}
