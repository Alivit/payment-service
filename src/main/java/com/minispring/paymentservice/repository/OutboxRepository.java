package com.minispring.paymentservice.repository;

import com.minispring.paymentservice.model.OutboxEvent;
import com.minispring.paymentservice.model.OutboxStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OutboxRepository extends MongoRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatus(OutboxStatus status, Pageable pageable);
}
