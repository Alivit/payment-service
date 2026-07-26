package com.minispring.paymentservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Getter
@Setter
@Document(collection = "payments")
public class Payment {

    @Id
    private UUID id = UUID.randomUUID();

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal paymentAmount;

    private PaymentStatus paymentStatus;
    private UUID orderId;
    private UUID userId;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
