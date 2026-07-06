package com.example.paymentservice.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Document(collection = "payments")
public class Payment {

    @Id
    UUID id = UUID.randomUUID();

    @Field("order_id")
    UUID orderId;

    @Field("user_id")
    UUID userId;

    @Field(name = "payment_amount", targetType = FieldType.DECIMAL128)
    BigDecimal paymentAmount;

    @Field("payment_status")
    PaymentStatus paymentStatus;

    @CreatedDate
    @Field("created_at")
    Instant createdAt;
}
