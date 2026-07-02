package com.example.paymentservice.repository;

import com.example.paymentservice.model.OutboxEvent;
import com.example.paymentservice.model.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends MongoRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatus(OutboxStatus status, Pageable pageable);
}
