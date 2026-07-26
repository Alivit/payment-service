package com.minispring.paymentservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.minispring.paymentservice.client.OrderGrpcClient;
import com.minispring.paymentservice.client.PaymentGatewayClient;
import com.minispring.paymentservice.dto.request.PaymentProcessRequest;
import com.minispring.paymentservice.dto.request.PaymentSearchCriteria;
import com.minispring.paymentservice.dto.response.OrderView;
import com.minispring.paymentservice.dto.response.PaymentTotalSum;
import com.minispring.paymentservice.dto.response.PaymentView;
import com.minispring.paymentservice.exception.ResourceNotFoundException;
import com.minispring.paymentservice.mapper.PaymentMapper;
import com.minispring.paymentservice.messaging.PaymentEventPublisher;
import com.minispring.paymentservice.model.Payment;
import com.minispring.paymentservice.model.PaymentStatus;
import com.minispring.paymentservice.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Mock
    private OrderGrpcClient orderGrpcClient;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Nested
    class CreateTest {
        private PaymentProcessRequest createDto;
        private UUID userId;
        private Payment paymentToProcess;
        private PaymentView expectedDto;

        @BeforeEach
        void init() {
            createDto = Instancio.create(PaymentProcessRequest.class);
            userId = UUID.randomUUID();
            paymentToProcess = Instancio.create(Payment.class);
            paymentToProcess.setUserId(userId);
            expectedDto = Instancio.create(PaymentView.class);
        }

        @Test
        void shouldThrowNotFoundIfOrderDoesNotExistInGrpc() {
            given(paymentRepository.findByOrderId(createDto.orderId())).willReturn(Optional.empty());
            given(orderGrpcClient.getOrderPriceById(createDto.orderId()))
                    .willThrow(new ResourceNotFoundException("Order not found in order-service"));

            assertThatThrownBy(() -> paymentService.create(userId, createDto))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(paymentMapper, paymentGatewayClient, transactionTemplate);
        }

        @Test
        void shouldThrowAccessDeniedIfGrpcOrderBelongsToSomeoneElse() {
            given(paymentRepository.findByOrderId(createDto.orderId())).willReturn(Optional.empty());

            OrderView orderDetails = new OrderView(createDto.orderId(), UUID.randomUUID(), BigDecimal.TEN);
            given(orderGrpcClient.getOrderPriceById(createDto.orderId())).willReturn(orderDetails);

            assertThatThrownBy(() -> paymentService.create(userId, createDto))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You do not have permission to pay for this order.");

            verifyNoInteractions(paymentMapper, paymentGatewayClient, transactionTemplate);
        }

        @Test
        void shouldReturnExistingSuccessIfAlreadyPaid() {
            paymentToProcess.setPaymentStatus(PaymentStatus.SUCCESS);

            given(paymentRepository.findByOrderId(createDto.orderId())).willReturn(Optional.of(paymentToProcess));
            given(paymentMapper.toView(paymentToProcess)).willReturn(expectedDto);

            PaymentView result = paymentService.create(userId, createDto);

            assertThat(result).isEqualTo(expectedDto);
            verifyNoInteractions(orderGrpcClient, paymentGatewayClient, transactionTemplate);
        }

        @Test
        void shouldCreateNewPaymentAndProcessSuccessfully() {
            paymentToProcess.setPaymentStatus(PaymentStatus.PENDING);

            given(paymentRepository.findByOrderId(createDto.orderId())).willReturn(Optional.empty());

            OrderView orderDetails = new OrderView(createDto.orderId(), userId, BigDecimal.TEN);
            given(orderGrpcClient.getOrderPriceById(createDto.orderId())).willReturn(orderDetails);
            given(paymentMapper.toNewPayment(orderDetails)).willReturn(paymentToProcess);
            given(paymentRepository.save(paymentToProcess)).willReturn(paymentToProcess);
            given(paymentGatewayClient.processPayment(paymentToProcess, createDto))
                    .willReturn(PaymentStatus.SUCCESS);
            given(transactionTemplate.execute(any())).willAnswer(invocation -> {
                TransactionCallback<PaymentView> callback = invocation.getArgument(0);
                return callback.doInTransaction(null);
            });
            given(paymentMapper.toView(paymentToProcess)).willReturn(expectedDto);

            PaymentView result = paymentService.create(userId, createDto);

            assertThat(result).isEqualTo(expectedDto);
            assertThat(paymentToProcess.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);

            verify(paymentRepository, org.mockito.Mockito.times(2)).save(paymentToProcess);
            verify(eventPublisher).publishPayment(paymentToProcess);
        }

        @Test
        void shouldThrowAccessDeniedIfExistingPaymentDoesNotBelongToUser() {
            paymentToProcess.setUserId(UUID.randomUUID());

            given(paymentRepository.findByOrderId(createDto.orderId())).willReturn(Optional.of(paymentToProcess));

            assertThatThrownBy(() -> paymentService.create(userId, createDto))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(orderGrpcClient, paymentGatewayClient);
        }

        @Test
        void shouldProcessExistingPendingPaymentSuccessfully() {
            paymentToProcess.setPaymentStatus(PaymentStatus.PENDING);

            given(paymentRepository.findByOrderId(createDto.orderId())).willReturn(Optional.of(paymentToProcess));
            given(paymentGatewayClient.processPayment(paymentToProcess, createDto))
                    .willReturn(PaymentStatus.SUCCESS);

            given(transactionTemplate.execute(any())).willAnswer(invocation -> {
                TransactionCallback<PaymentView> callback = invocation.getArgument(0);
                return callback.doInTransaction(null);
            });
            given(paymentMapper.toView(paymentToProcess)).willReturn(expectedDto);

            PaymentView result = paymentService.create(userId, createDto);

            assertThat(result).isEqualTo(expectedDto);
            assertThat(paymentToProcess.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);

            verifyNoInteractions(orderGrpcClient);
            verify(paymentRepository).save(paymentToProcess);
            verify(eventPublisher).publishPayment(paymentToProcess);
        }

        @Test
        void shouldBubbleUpOptimisticLockingExceptionWhenConcurrentUpdateOccurs() {
            paymentToProcess.setPaymentStatus(PaymentStatus.PENDING);

            given(paymentRepository.findByOrderId(createDto.orderId())).willReturn(Optional.of(paymentToProcess));
            given(paymentGatewayClient.processPayment(paymentToProcess, createDto))
                    .willReturn(PaymentStatus.SUCCESS);

            given(transactionTemplate.execute(any())).willAnswer(invocation -> {
                TransactionCallback<PaymentView> callback = invocation.getArgument(0);
                return callback.doInTransaction(null);
            });

            given(paymentRepository.save(paymentToProcess))
                    .willThrow(new OptimisticLockingFailureException("Concurrent update"));

            assertThatThrownBy(() -> paymentService.create(userId, createDto))
                    .isInstanceOf(OptimisticLockingFailureException.class);

            verifyNoInteractions(eventPublisher);
        }

        @Test
        void shouldFailTransactionIfOutboxEventFails() {
            paymentToProcess.setPaymentStatus(PaymentStatus.PENDING);

            given(paymentRepository.findByOrderId(createDto.orderId())).willReturn(Optional.of(paymentToProcess));
            given(paymentGatewayClient.processPayment(paymentToProcess, createDto))
                    .willReturn(PaymentStatus.SUCCESS);

            given(transactionTemplate.execute(any())).willAnswer(invocation -> {
                TransactionCallback<PaymentView> callback = invocation.getArgument(0);
                return callback.doInTransaction(null);
            });

            given(paymentRepository.save(paymentToProcess)).willReturn(paymentToProcess);

            org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("Mongo disk full"))
                    .when(eventPublisher)
                    .publishPayment(paymentToProcess);

            assertThatThrownBy(() -> paymentService.create(userId, createDto))
                    .isInstanceOf(DataAccessResourceFailureException.class);
        }
    }

    @Nested
    class GetByOrderIdTest {

        @Test
        void shouldReturnSinglePaymentDto() {
            UUID orderId = UUID.randomUUID();
            Payment payment = Instancio.create(Payment.class);
            PaymentView expectedDto = Instancio.create(PaymentView.class);

            given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.of(payment));
            given(paymentMapper.toView(payment)).willReturn(expectedDto);

            PaymentView result = paymentService.getByOrderId(orderId);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
        }

        @Test
        void shouldThrowExceptionWhenNotFound() {
            UUID orderId = UUID.randomUUID();
            given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getByOrderId(orderId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class GetByOrderIdAndUserIdTest {
        @Test
        void shouldThrowAccessDeniedWhenOrderBelongsToSomeoneElse() {
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            given(paymentRepository.existsByOrderIdAndUserIdNot(orderId, userId))
                    .willReturn(true);

            assertThatThrownBy(() -> paymentService.getByOrderIdAndUserId(orderId, userId))
                    .isInstanceOf(AccessDeniedException.class);

            verify(paymentRepository, never()).findByOrderIdAndUserId(any(), any());
        }

        @Test
        void shouldReturnSinglePaymentDtoWhenUserOwnsOrder() {
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Payment payment = Instancio.create(Payment.class);
            PaymentView expectedDto = Instancio.create(PaymentView.class);

            given(paymentRepository.existsByOrderIdAndUserIdNot(orderId, userId))
                    .willReturn(false);
            given(paymentRepository.findByOrderIdAndUserId(orderId, userId)).willReturn(Optional.of(payment));
            given(paymentMapper.toView(payment)).willReturn(expectedDto);

            PaymentView result = paymentService.getByOrderIdAndUserId(orderId, userId);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
        }
    }

    @Nested
    class GetByUserIdTest {

        @Test
        void getByUserIdShouldReturnPageOfPaymentResponseDto() {
            UUID userId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);
            List<Payment> payments = Instancio.ofList(Payment.class).size(2).create();
            List<PaymentView> expectedList =
                    Instancio.ofList(PaymentView.class).size(2).create();
            Page<Payment> paymentPage = new PageImpl<>(payments, pageable, payments.size());

            given(paymentRepository.findByUserId(userId, pageable)).willReturn(paymentPage);
            given(paymentMapper.toView(payments.get(0))).willReturn(expectedList.get(0));
            given(paymentMapper.toView(payments.get(1))).willReturn(expectedList.get(1));

            Page<PaymentView> result = paymentService.getByUserId(userId, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).containsExactlyElementsOf(expectedList);
        }

        @Test
        void getByUserIdShouldReturnEmptyPageWhenNoPaymentsFound() {
            UUID userId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);

            given(paymentRepository.findByUserId(userId, pageable)).willReturn(Page.empty(pageable));

            Page<PaymentView> result = paymentService.getByUserId(userId, pageable);

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
            List<PaymentView> expectedList =
                    Instancio.ofList(PaymentView.class).size(3).create();
            Page<Payment> paymentPage = new PageImpl<>(payments, pageable, payments.size());

            given(paymentRepository.findByPaymentStatus(status, pageable)).willReturn(paymentPage);
            for (int i = 0; i < payments.size(); i++) {
                given(paymentMapper.toView(payments.get(i))).willReturn(expectedList.get(i));
            }

            Page<PaymentView> result = paymentService.getByStatus(status, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getContent()).containsExactlyElementsOf(expectedList);
        }

        @Test
        void getByStatusShouldReturnEmptyPageWhenNoPaymentsFound() {
            PaymentStatus status = PaymentStatus.FAILED;
            Pageable pageable = PageRequest.of(0, 10);

            given(paymentRepository.findByPaymentStatus(status, pageable)).willReturn(Page.empty(pageable));

            Page<PaymentView> result = paymentService.getByStatus(status, pageable);

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
            List<PaymentView> expectedList =
                    Instancio.ofList(PaymentView.class).size(2).create();
            Page<Payment> paymentPage = new PageImpl<>(payments, pageable, payments.size());

            given(paymentRepository.findByUserIdAndPaymentStatus(userId, status, pageable))
                    .willReturn(paymentPage);
            given(paymentMapper.toView(payments.get(0))).willReturn(expectedList.get(0));
            given(paymentMapper.toView(payments.get(1))).willReturn(expectedList.get(1));

            Page<PaymentView> result = paymentService.getByUserIdAndStatus(userId, status, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).containsExactlyElementsOf(expectedList);
        }

        @Test
        void getByUserIdAndStatusShouldReturnEmptyPageWhenNoPaymentsFound() {
            UUID userId = UUID.randomUUID();
            PaymentStatus status = PaymentStatus.SUCCESS;
            Pageable pageable = PageRequest.of(0, 10);

            given(paymentRepository.findByUserIdAndPaymentStatus(userId, status, pageable))
                    .willReturn(Page.empty(pageable));

            Page<PaymentView> result = paymentService.getByUserIdAndStatus(userId, status, pageable);

            assertThat(result).isNotNull().isEmpty();
            verifyNoInteractions(paymentMapper);
        }
    }

    @Nested
    class GetTotalAmountByUserIdTest {

        @Test
        void getTotalAmountByUserIdShouldReturnCalculatedAmountWhenPaymentsExist() {
            UUID userId = UUID.randomUUID();
            PaymentSearchCriteria request = Instancio.of(PaymentSearchCriteria.class)
                    .set(field(PaymentSearchCriteria::from), Instant.now().minus(30, ChronoUnit.DAYS))
                    .set(field(PaymentSearchCriteria::to), Instant.now())
                    .create();
            PaymentTotalSum expectedAmount = new PaymentTotalSum(new BigDecimal("150.50"));

            given(paymentRepository.sumPaymentsByUserIdAndDateRange(userId, request))
                    .willReturn(Optional.of(expectedAmount));

            PaymentTotalSum result = paymentService.getTotalSumByUserId(userId, request);

            assertThat(result).isNotNull();
            assertThat(result.totalSum()).isEqualTo(new BigDecimal("150.50"));
        }

        @Test
        void getTotalAmountByUserIdShouldReturnZeroWhenNoPaymentsExist() {
            UUID userId = UUID.randomUUID();
            PaymentSearchCriteria request = Instancio.of(PaymentSearchCriteria.class)
                    .set(field(PaymentSearchCriteria::from), Instant.now().minus(30, ChronoUnit.DAYS))
                    .set(field(PaymentSearchCriteria::to), Instant.now())
                    .create();

            given(paymentRepository.sumPaymentsByUserIdAndDateRange(userId, request))
                    .willReturn(Optional.empty());

            PaymentTotalSum result = paymentService.getTotalSumByUserId(userId, request);

            assertThat(result).isNotNull();
            assertThat(result.totalSum()).isEqualTo(BigDecimal.ZERO);
        }
    }

    @Nested
    class GetTotalAmountTest {

        @Test
        void getTotalAmountShouldReturnCalculatedAmountWhenPaymentsExist() {
            PaymentSearchCriteria request = Instancio.of(PaymentSearchCriteria.class)
                    .set(field(PaymentSearchCriteria::from), Instant.now().minus(30, ChronoUnit.DAYS))
                    .set(field(PaymentSearchCriteria::to), Instant.now())
                    .create();
            PaymentTotalSum expectedAmount = new PaymentTotalSum(new BigDecimal("1000.00"));

            given(paymentRepository.sumAllPaymentsByDateRange(request)).willReturn(Optional.of(expectedAmount));

            PaymentTotalSum result = paymentService.getTotalSum(request);

            assertThat(result).isNotNull();
            assertThat(result.totalSum()).isEqualTo(new BigDecimal("1000.00"));
        }

        @Test
        void getTotalAmountShouldReturnZeroWhenNoPaymentsExist() {
            PaymentSearchCriteria request = Instancio.of(PaymentSearchCriteria.class)
                    .set(field(PaymentSearchCriteria::from), Instant.now().minus(30, ChronoUnit.DAYS))
                    .set(field(PaymentSearchCriteria::to), Instant.now())
                    .create();

            given(paymentRepository.sumAllPaymentsByDateRange(request)).willReturn(Optional.empty());

            PaymentTotalSum result = paymentService.getTotalSum(request);

            assertThat(result).isNotNull();
            assertThat(result.totalSum()).isEqualTo(BigDecimal.ZERO);
        }
    }
}
