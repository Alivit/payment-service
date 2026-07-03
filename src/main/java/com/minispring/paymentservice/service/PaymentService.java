package com.minispring.paymentservice.service;

import com.minispring.paymentservice.dto.request.PaymentProcessRequest;
import com.minispring.paymentservice.dto.request.PaymentSearchCriteria;
import com.minispring.paymentservice.dto.response.PaymentTotalSum;
import com.minispring.paymentservice.dto.response.PaymentView;
import com.minispring.paymentservice.model.PaymentStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {

    PaymentView create(UUID userId, PaymentProcessRequest request);

    PaymentView getByOrderId(UUID orderId);

    PaymentView getByOrderIdAndUserId(UUID orderId, UUID userId);

    Page<PaymentView> getByUserId(UUID userId, Pageable pageable);

    Page<PaymentView> getByStatus(PaymentStatus status, Pageable pageable);

    Page<PaymentView> getByUserIdAndStatus(UUID userId, PaymentStatus status, Pageable pageable);

    PaymentTotalSum getTotalSumByUserId(UUID userId, PaymentSearchCriteria request);

    PaymentTotalSum getTotalSum(PaymentSearchCriteria request);
}
