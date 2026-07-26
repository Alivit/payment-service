package com.minispring.paymentservice.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minispring.paymentservice.mapper.PaymentMapper;
import com.minispring.paymentservice.messaging.event.PaymentCreateEvent;
import com.minispring.paymentservice.model.OutboxEvent;
import com.minispring.paymentservice.model.OutboxStatus;
import com.minispring.paymentservice.model.Payment;
import com.minispring.paymentservice.repository.OutboxRepository;
import java.util.Map;
import java.util.UUID;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class KafkaPaymentEventPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private KafkaPaymentEventPublisher publisher;

    @Captor
    private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

    @Test
    @SuppressWarnings("unchecked")
    void shouldCreateAndSaveOutboxEventWithPendingStatus() {
        UUID orderId = UUID.randomUUID();
        Payment payment = Instancio.create(Payment.class);
        payment.setOrderId(orderId);

        PaymentCreateEvent createEvent = Instancio.create(PaymentCreateEvent.class);
        Map<String, Object> expectedPayload = Map.of("amount", 100.0, "status", "SUCCESS");

        when(paymentMapper.toEvent(payment)).thenReturn(createEvent);
        when(jsonMapper.convertValue(eq(createEvent), any(TypeReference.class))).thenReturn(expectedPayload);

        publisher.publishPayment(payment);

        verify(outboxRepository).save(outboxEventCaptor.capture());
        OutboxEvent savedEvent = outboxEventCaptor.getValue();

        assertThat(savedEvent.getId()).isNotNull();
        assertThat(savedEvent.getAggregateId()).isEqualTo(orderId.toString());
        assertThat(savedEvent.getEventType()).isEqualTo("CREATE_PAYMENT");
        assertThat(savedEvent.getPayload()).isEqualTo(expectedPayload);
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowExceptionAndNotSaveWhenSerializationFails() {
        UUID orderId = UUID.randomUUID();
        Payment payment = Instancio.create(Payment.class);
        payment.setOrderId(orderId);

        PaymentCreateEvent createEvent = Instancio.create(PaymentCreateEvent.class);

        when(paymentMapper.toEvent(payment)).thenReturn(createEvent);

        IllegalArgumentException jacksonError = new IllegalArgumentException("Jackson mapping error");
        when(jsonMapper.convertValue(eq(createEvent), any(TypeReference.class))).thenThrow(jacksonError);

        assertThatThrownBy(() -> publisher.publishPayment(payment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not create outbox event for payment processing")
                .hasCause(jacksonError);

        verify(outboxRepository, never()).save(any());
    }
}
