package com.minispring.paymentservice.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Builder
@Document(collection = "outboxEvents")
public class OutboxEvent {

    @Id
    private UUID id;

    private String aggregateId;
    private String eventType;
    private Map<String, Object> payload;
    private OutboxStatus status;

    @CreatedDate
    private Instant createdAt;
}
