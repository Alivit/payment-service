package com.minispring.paymentservice.service.impl;

import com.minispring.paymentservice.client.PaymentGatewayClient;
import com.minispring.paymentservice.dto.PaymentCreateDto;
import com.minispring.paymentservice.dto.PaymentFilterRequestDto;
import com.minispring.paymentservice.dto.PaymentResponseDto;
import com.minispring.paymentservice.dto.TotalAmountDto;
import com.minispring.paymentservice.mapper.PaymentMapper;
import com.minispring.paymentservice.messaging.PaymentEventPublisher;
import com.minispring.paymentservice.model.Payment;
import com.minispring.paymentservice.model.PaymentStatus;
import com.minispring.paymentservice.repository.PaymentRepository;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private PaymentGatewayClient paymentGatewayClient;

    @Mock
    private PaymentEventPublisher eventPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Nested
    class CreateTest {

        private PaymentCreateDto createDto;
        private Payment payment;
        private PaymentResponseDto expectedDto;

        @BeforeEach
        void init() {
            createDto = Instancio.create(PaymentCreateDto.class);
            payment = Instancio.create(Payment.class);
            expectedDto = Instancio.create(PaymentResponseDto.class);
        }

        @Test
        void createShouldReturnExistingSuccessPaymentIfItExists() {
            Payment existingSuccessPayment = Instancio.create(Payment.class);
            existingSuccessPayment.setPaymentStatus(PaymentStatus.SUCCESS);

            given(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(createDto.orderId()))
                    .willReturn(Optional.of(existingSuccessPayment));
            given(paymentMapper.paymentToPaymentResponseDto(existingSuccessPayment)).willReturn(expectedDto);

            PaymentResponseDto result = paymentService.create(createDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);

            verifyNoInteractions(paymentGatewayClient, eventPublisher, transactionTemplate);
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        void createShouldProcessNewPaymentSuccessfullyWhenGatewayReturnsSuccess() {
            given(transactionTemplate.execute(any())).willAnswer(invocation -> {
                TransactionCallback<PaymentResponseDto> callback = invocation.getArgument(0);
                return callback.doInTransaction(null);
            });

            given(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(createDto.orderId()))
                    .willReturn(Optional.empty());
            given(paymentMapper.paymentCreateDtoToPayment(createDto)).willReturn(payment);
            given(paymentRepository.save(payment)).willReturn(payment);
            given(paymentGatewayClient.processPayment(payment)).willReturn(PaymentStatus.SUCCESS);
            given(paymentMapper.paymentToPaymentResponseDto(payment)).willReturn(expectedDto);

            PaymentResponseDto result = paymentService.create(createDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);

            verify(paymentRepository, times(2)).save(payment);
            verify(paymentGatewayClient).processPayment(payment);
            verify(eventPublisher).publishPayment(payment);
        }

        @Test
        void createShouldProcessNewPaymentWhenGatewayReturnsFailed() {
            given(transactionTemplate.execute(any())).willAnswer(invocation -> {
                TransactionCallback<PaymentResponseDto> callback = invocation.getArgument(0);
                return callback.doInTransaction(null);
            });

            given(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(createDto.orderId()))
                    .willReturn(Optional.empty());
            given(paymentMapper.paymentCreateDtoToPayment(createDto)).willReturn(payment);
            given(paymentRepository.save(payment)).willReturn(payment);
            given(paymentGatewayClient.processPayment(payment)).willReturn(PaymentStatus.FAILED);
            given(paymentMapper.paymentToPaymentResponseDto(payment)).willReturn(expectedDto);

            PaymentResponseDto result = paymentService.create(createDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);

            verify(eventPublisher).publishPayment(payment);
        }

        @Test
        void createShouldProcessNewPaymentWhenPreviousPaymentFailed() {
            Payment existingFailedPayment = Instancio.create(Payment.class);
            existingFailedPayment.setPaymentStatus(PaymentStatus.FAILED);

            given(transactionTemplate.execute(any())).willAnswer(invocation -> {
                TransactionCallback<PaymentResponseDto> callback = invocation.getArgument(0);
                return callback.doInTransaction(null);
            });

            given(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(createDto.orderId()))
                    .willReturn(Optional.of(existingFailedPayment));
            given(paymentMapper.paymentCreateDtoToPayment(createDto)).willReturn(payment);
            given(paymentRepository.save(payment)).willReturn(payment);
            given(paymentGatewayClient.processPayment(payment)).willReturn(PaymentStatus.SUCCESS);
            given(paymentMapper.paymentToPaymentResponseDto(payment)).willReturn(expectedDto);

            PaymentResponseDto result = paymentService.create(createDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            verify(paymentGatewayClient).processPayment(payment);
        }
    }

    @Nested
    class GetByOrderIdTest {

        @Test
        void getByOrderIdShouldReturnListSortedDesc() {
            UUID orderId = UUID.randomUUID();
            boolean desc = true;
            Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

            List<Payment> payments = Instancio.ofList(Payment.class).size(3).create();
            List<PaymentResponseDto> expectedList = Instancio.ofList(PaymentResponseDto.class).size(3).create();

            given(paymentRepository.findByOrderId(orderId, sort)).willReturn(payments);
            for (int i = 0; i < payments.size(); i++) {
                given(paymentMapper.paymentToPaymentResponseDto(payments.get(i))).willReturn(expectedList.get(i));
            }

            List<PaymentResponseDto> result = paymentService.getByOrderId(orderId, desc);

            assertThat(result).isNotNull().hasSize(3).containsExactlyElementsOf(expectedList);
            verify(paymentRepository).findByOrderId(orderId, sort);
        }

        @Test
        void getByOrderIdShouldReturnListSortedAsc() {
            UUID orderId = UUID.randomUUID();
            boolean desc = false;
            Sort sort = Sort.by(Sort.Direction.ASC, "createdAt");

            List<Payment> payments = Instancio.ofList(Payment.class).size(2).create();
            List<PaymentResponseDto> expectedList = Instancio.ofList(PaymentResponseDto.class).size(2).create();

            given(paymentRepository.findByOrderId(orderId, sort)).willReturn(payments);
            for (int i = 0; i < payments.size(); i++) {
                given(paymentMapper.paymentToPaymentResponseDto(payments.get(i))).willReturn(expectedList.get(i));
            }

            List<PaymentResponseDto> result = paymentService.getByOrderId(orderId, desc);

            assertThat(result).isNotNull().hasSize(2).containsExactlyElementsOf(expectedList);
            verify(paymentRepository).findByOrderId(orderId, sort);
        }

        @Test
        void getByOrderIdShouldReturnEmptyListWhenNoPaymentsFound() {
            UUID orderId = UUID.randomUUID();
            Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

            given(paymentRepository.findByOrderId(orderId, sort)).willReturn(List.of());

            List<PaymentResponseDto> result = paymentService.getByOrderId(orderId, true);

            assertThat(result).isNotNull().isEmpty();
            verifyNoInteractions(paymentMapper);
        }
    }

    @Nested
    class GetByOrderIdAndUserIdTest {

        @Test
        void getByOrderIdAndUserIdShouldThrowAccessDeniedExceptionWhenOrderBelongsToSomeoneElse() {
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            given(paymentRepository.existsByOrderIdAndUserIdNot(orderId, userId)).willReturn(true);

            assertThatThrownBy(() -> paymentService.getByOrderIdAndUserId(orderId, userId, true))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You do not have permission to access payments for this order.");

            verify(paymentRepository, never()).findByOrderIdAndUserId(any(), any(), any());
            verifyNoInteractions(paymentMapper);
        }

        @Test
        void getByOrderIdAndUserIdShouldReturnListSortedDescWhenUserOwnsOrder() {
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            boolean desc = true;
            Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

            List<Payment> payments = Instancio.ofList(Payment.class).size(2).create();
            List<PaymentResponseDto> expectedList = Instancio.ofList(PaymentResponseDto.class).size(2).create();

            given(paymentRepository.existsByOrderIdAndUserIdNot(orderId, userId)).willReturn(false);
            given(paymentRepository.findByOrderIdAndUserId(orderId, userId, sort)).willReturn(payments);
            for (int i = 0; i < payments.size(); i++) {
                given(paymentMapper.paymentToPaymentResponseDto(payments.get(i))).willReturn(expectedList.get(i));
            }

            List<PaymentResponseDto> result = paymentService.getByOrderIdAndUserId(orderId, userId, desc);

            assertThat(result).isNotNull().hasSize(2).containsExactlyElementsOf(expectedList);
            verify(paymentRepository).findByOrderIdAndUserId(orderId, userId, sort);
        }

        @Test
        void getByOrderIdAndUserIdShouldReturnListSortedAscWhenUserOwnsOrder() {
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            boolean desc = false;
            Sort sort = Sort.by(Sort.Direction.ASC, "createdAt");

            List<Payment> payments = Instancio.ofList(Payment.class).size(1).create();
            List<PaymentResponseDto> expectedList = Instancio.ofList(PaymentResponseDto.class).size(1).create();

            given(paymentRepository.existsByOrderIdAndUserIdNot(orderId, userId)).willReturn(false);
            given(paymentRepository.findByOrderIdAndUserId(orderId, userId, sort)).willReturn(payments);
            given(paymentMapper.paymentToPaymentResponseDto(payments.getFirst())).willReturn(expectedList.getFirst());

            List<PaymentResponseDto> result = paymentService.getByOrderIdAndUserId(orderId, userId, desc);

            assertThat(result).isNotNull().hasSize(1).containsExactlyElementsOf(expectedList);
        }
    }

    @Nested
    class GetByUserIdTest {

        @Test
        void getByUserIdShouldReturnPageOfPaymentResponseDto() {
            UUID userId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);
            List<Payment> payments = Instancio.ofList(Payment.class).size(2).create();
            List<PaymentResponseDto> expectedList = Instancio.ofList(PaymentResponseDto.class).size(2).create();
            Page<Payment> paymentPage = new PageImpl<>(payments, pageable, payments.size());

            given(paymentRepository.findByUserId(userId, pageable)).willReturn(paymentPage);
            given(paymentMapper.paymentToPaymentResponseDto(payments.get(0))).willReturn(expectedList.get(0));
            given(paymentMapper.paymentToPaymentResponseDto(payments.get(1))).willReturn(expectedList.get(1));

            Page<PaymentResponseDto> result = paymentService.getByUserId(userId, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).containsExactlyElementsOf(expectedList);
        }

        @Test
        void getByUserIdShouldReturnEmptyPageWhenNoPaymentsFound() {
            UUID userId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);

            given(paymentRepository.findByUserId(userId, pageable)).willReturn(Page.empty(pageable));

            Page<PaymentResponseDto> result = paymentService.getByUserId(userId, pageable);

            assertThat(result).isNotNull().isEmpty();
            verifyNoInteractions(paymentMapper);
        }
    }

    @Nested
    class GetByStatusTest {

        @Test
        void getByStatusShouldReturnPageOfPaymentResponseDto() {
            PaymentStatus status = PaymentStatus.SUCCESS;
            Pageable pageable = PageRequest.of(0, 10);
            List<Payment> payments = Instancio.ofList(Payment.class).size(3).create();
            List<PaymentResponseDto> expectedList = Instancio.ofList(PaymentResponseDto.class).size(3).create();
            Page<Payment> paymentPage = new PageImpl<>(payments, pageable, payments.size());

            given(paymentRepository.findByPaymentStatus(status, pageable)).willReturn(paymentPage);
            for (int i = 0; i < payments.size(); i++) {
                given(paymentMapper.paymentToPaymentResponseDto(payments.get(i))).willReturn(expectedList.get(i));
            }

            Page<PaymentResponseDto> result = paymentService.getByStatus(status, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getContent()).containsExactlyElementsOf(expectedList);
        }

        @Test
        void getByStatusShouldReturnEmptyPageWhenNoPaymentsFound() {
            PaymentStatus status = PaymentStatus.FAILED;
            Pageable pageable = PageRequest.of(0, 10);

            given(paymentRepository.findByPaymentStatus(status, pageable)).willReturn(Page.empty(pageable));

            Page<PaymentResponseDto> result = paymentService.getByStatus(status, pageable);

            assertThat(result).isNotNull().isEmpty();
            verifyNoInteractions(paymentMapper);
        }
    }

    @Nested
    class GetByUserIdAndStatusTest {

        @Test
        void getByUserIdAndStatusShouldReturnPageOfPaymentResponseDto() {
            UUID userId = UUID.randomUUID();
            PaymentStatus status = PaymentStatus.PENDING;
            Pageable pageable = PageRequest.of(0, 10);

            List<Payment> payments = Instancio.ofList(Payment.class).size(2).create();
            List<PaymentResponseDto> expectedList = Instancio.ofList(PaymentResponseDto.class).size(2).create();
            Page<Payment> paymentPage = new PageImpl<>(payments, pageable, payments.size());

            given(paymentRepository.findByUserIdAndPaymentStatus(userId, status, pageable)).willReturn(paymentPage);
            given(paymentMapper.paymentToPaymentResponseDto(payments.get(0))).willReturn(expectedList.get(0));
            given(paymentMapper.paymentToPaymentResponseDto(payments.get(1))).willReturn(expectedList.get(1));

            Page<PaymentResponseDto> result = paymentService.getByUserIdAndStatus(userId, status, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).containsExactlyElementsOf(expectedList);
        }

        @Test
        void getByUserIdAndStatusShouldReturnEmptyPageWhenNoPaymentsFound() {
            UUID userId = UUID.randomUUID();
            PaymentStatus status = PaymentStatus.SUCCESS;
            Pageable pageable = PageRequest.of(0, 10);

            given(paymentRepository.findByUserIdAndPaymentStatus(userId, status, pageable)).willReturn(Page.empty(pageable));

            Page<PaymentResponseDto> result = paymentService.getByUserIdAndStatus(userId, status, pageable);

            assertThat(result).isNotNull().isEmpty();
            verifyNoInteractions(paymentMapper);
        }
    }

    @Nested
    class GetTotalAmountByUserIdTest {

        @Test
        void getTotalAmountByUserIdShouldReturnCalculatedAmountWhenPaymentsExist() {
            UUID userId = UUID.randomUUID();
            PaymentFilterRequestDto request = Instancio.create(PaymentFilterRequestDto.class);
            TotalAmountDto expectedAmount = new TotalAmountDto(new BigDecimal("150.50"));

            given(paymentRepository.sumPaymentsByUserIdAndDateRange(userId, request))
                    .willReturn(Optional.of(expectedAmount));

            TotalAmountDto result = paymentService.getTotalAmountByUserId(userId, request);

            assertThat(result).isNotNull();
            assertThat(result.totalAmount()).isEqualTo(new BigDecimal("150.50"));
        }

        @Test
        void getTotalAmountByUserIdShouldReturnZeroWhenNoPaymentsExist() {
            UUID userId = UUID.randomUUID();
            PaymentFilterRequestDto request = Instancio.create(PaymentFilterRequestDto.class);

            given(paymentRepository.sumPaymentsByUserIdAndDateRange(userId, request))
                    .willReturn(Optional.empty());

            TotalAmountDto result = paymentService.getTotalAmountByUserId(userId, request);

            assertThat(result).isNotNull();
            assertThat(result.totalAmount()).isEqualTo(BigDecimal.ZERO);
        }
    }

    @Nested
    class GetTotalAmountTest {

        @Test
        void getTotalAmountShouldReturnCalculatedAmountWhenPaymentsExist() {
            PaymentFilterRequestDto request = Instancio.create(PaymentFilterRequestDto.class);
            TotalAmountDto expectedAmount = new TotalAmountDto(new BigDecimal("1000.00"));

            given(paymentRepository.sumAllPaymentsByDateRange(request))
                    .willReturn(Optional.of(expectedAmount));

            TotalAmountDto result = paymentService.getTotalAmount(request);

            assertThat(result).isNotNull();
            assertThat(result.totalAmount()).isEqualTo(new BigDecimal("1000.00"));
        }

        @Test
        void getTotalAmountShouldReturnZeroWhenNoPaymentsExist() {
            PaymentFilterRequestDto request = Instancio.create(PaymentFilterRequestDto.class);

            given(paymentRepository.sumAllPaymentsByDateRange(request))
                    .willReturn(Optional.empty());

            TotalAmountDto result = paymentService.getTotalAmount(request);

            assertThat(result).isNotNull();
            assertThat(result.totalAmount()).isEqualTo(BigDecimal.ZERO);
        }
    }
}