package com.example.paymentservice.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.UUID;


@Getter
@Setter
@Builder
@Document(collection = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Field("aggregate_id")
    private String aggregateId;

    @Field("event_type")
    private String eventType;
    private String payload;
    private OutboxStatus status;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;
}
