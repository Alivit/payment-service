package com.minispring.paymentservice.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

import com.minispring.paymentservice.BaseIntegrationTest;
import com.minispring.paymentservice.messaging.PaymentEventPublisher;
import com.minispring.paymentservice.model.OutboxEvent;
import com.minispring.paymentservice.model.OutboxStatus;
import com.minispring.paymentservice.model.Payment;
import com.minispring.paymentservice.repository.OutboxRepository;
import com.minispring.paymentservice.repository.PaymentRepository;
import java.util.UUID;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class KafkaPaymentEventPublisherIT extends BaseIntegrationTest {

    @Autowired
    private PaymentEventPublisher publisher;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @Transactional
    void shouldPersistOutboxEventToDatabase() {
        Payment payment = Instancio.of(Payment.class)
                .ignore(field(Payment::getVersion))
                .set(field(Payment::getOrderId), UUID.randomUUID())
                .create();

        paymentRepository.save(payment);
        publisher.publishPayment(payment);

        var outboxEvents = outboxRepository.findAll();

        assertThat(outboxEvents).hasSize(1);
        OutboxEvent event = outboxEvents.getFirst();

        assertThat(event.getAggregateId()).isEqualTo(payment.getOrderId().toString());
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getPayload()).isNotNull();
    }
}
