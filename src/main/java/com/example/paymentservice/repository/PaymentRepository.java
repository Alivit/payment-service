package com.example.paymentservice.repository;

import com.example.paymentservice.dto.PaymentFilterRequestDto;
import com.example.paymentservice.dto.TotalAmountDto;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends MongoRepository<Payment, UUID> {

    Page<Payment> findByUserId(UUID userId, Pageable pageable);

    Page<Payment> findByPaymentStatus(PaymentStatus paymentStatus, Pageable pageable);

    Page<Payment> findByUserIdAndPaymentStatus(UUID userId, PaymentStatus paymentStatus, Pageable pageable);

    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);

    List<Payment> findByOrderId(UUID orderId, Sort sort);

    List<Payment> findByOrderIdAndUserId(UUID orderId, UUID userId, Sort sort);

    boolean existsByOrderIdAndUserIdNot(UUID orderId, UUID userId);

    @Aggregation(pipeline = {
            "{ '$match': { 'user_id': ?0, 'created_at': { '$gte': ?#{#filter.from}, '$lte': ?#{#filter.to} }, 'payment_status': 'SUCCESS' } }",
            "{ '$group': { '_id': null, 'totalAmount': { '$sum': '$payment_amount' } } }"
    })
    Optional<TotalAmountDto> sumPaymentsByUserIdAndDateRange(UUID userId, PaymentFilterRequestDto filter);

    @Aggregation(pipeline = {
            "{ '$match': { 'created_at': { '$gte': ?#{#filter.from}, '$lte': ?#{#filter.to} }, 'payment_status': 'SUCCESS' } }",
            "{ '$group': { '_id': null, 'totalAmount': { '$sum': '$payment_amount' } } }"
    })
    Optional<TotalAmountDto> sumAllPaymentsByDateRange(PaymentFilterRequestDto filter);
}
