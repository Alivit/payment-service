package com.minispring.paymentservice.repository;

import com.minispring.paymentservice.dto.request.PaymentSearchCriteria;
import com.minispring.paymentservice.dto.response.PaymentTotalSum;
import com.minispring.paymentservice.model.Payment;
import com.minispring.paymentservice.model.PaymentStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends MongoRepository<Payment, UUID> {

    Page<Payment> findByUserId(UUID userId, Pageable pageable);

    Page<Payment> findByPaymentStatus(PaymentStatus paymentStatus, Pageable pageable);

    Page<Payment> findByUserIdAndPaymentStatus(UUID userId, PaymentStatus paymentStatus, Pageable pageable);

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByOrderIdAndUserId(UUID orderId, UUID userId);

    boolean existsByOrderIdAndUserIdNot(UUID orderId, UUID userId);

    @Aggregation(
            pipeline = {
                "{ '$match': { 'userId': ?0, 'createdAt': { '$gte': ?#{#filter.from}, '$lte': ?#{#filter.to} }, 'paymentStatus': 'SUCCESS' } }",
                "{ '$group': { '_id': null, 'totalSum': { '$sum': '$paymentAmount' } } }"
            })
    Optional<PaymentTotalSum> sumPaymentsByUserIdAndDateRange(UUID userId, PaymentSearchCriteria filter);

    @Aggregation(
            pipeline = {
                "{ '$match': { 'createdAt': { '$gte': ?#{#filter.from}, '$lte': ?#{#filter.to} }, 'paymentStatus': 'SUCCESS' } }",
                "{ '$group': { '_id': null, 'totalSum': { '$sum': '$paymentAmount' } } }"
            })
    Optional<PaymentTotalSum> sumAllPaymentsByDateRange(PaymentSearchCriteria filter);
}
