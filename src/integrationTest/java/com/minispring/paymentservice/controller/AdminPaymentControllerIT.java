package com.minispring.paymentservice.controller;

import com.minispring.paymentservice.BaseIntegrationTest;
import com.minispring.paymentservice.model.Payment;
import com.minispring.paymentservice.model.PaymentStatus;
import com.minispring.paymentservice.repository.PaymentRepository;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.instancio.Select.field;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@AutoConfigureMockMvc
public class AdminPaymentControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @Autowired
    private PaymentRepository paymentRepository;

    private static final String BASE_URL = "/api/v1/admin/payments";
    private UUID adminId;

    @BeforeEach
    public void init() {
        adminId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        paymentRepository.deleteAll();
    }

    private RequestPostProcessor adminJwt() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .jwt(builder -> builder
                        .subject(adminId.toString())
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                        .claim("preferred_username", "admin"));
    }

    private Payment createAndSavePayment(UUID userId, UUID orderId, PaymentStatus status, String amount, Instant createdAt) {
        Payment payment = Instancio.of(Payment.class)
                .ignore(field(Payment::getId))
                .set(field(Payment::getUserId), userId)
                .set(field(Payment::getOrderId), orderId)
                .set(field(Payment::getPaymentStatus), status)
                .set(field(Payment::getPaymentAmount), new BigDecimal(amount))
                .set(field(Payment::getCreatedAt), createdAt != null ? createdAt : Instant.now())
                .create();
        return paymentRepository.save(payment);
    }

    @Nested
    class GetPaymentsByUserIdTests {

        @Test
        void shouldReturnUserPayments() {
            UUID userId = UUID.randomUUID();
            createAndSavePayment(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "150.00", null);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/users/{userId}", userId)
                    .with(adminJwt())
                    .param("page", "0")
                    .param("size", "10")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying("$.content[0].userId", id -> assertThat(id).isEqualTo(userId.toString()));
        }

        @Test
        void shouldApplyPagination() {
            UUID userId = UUID.randomUUID();
            createAndSavePayment(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "10.00", null);
            createAndSavePayment(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "20.00", null);
            createAndSavePayment(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "30.00", null);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/users/{userId}", userId)
                    .with(adminJwt())
                    .param("page", "0")
                    .param("size", "2")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(3))
                    .hasPathSatisfying("$.content", content -> assertThat(content).asInstanceOf(LIST).hasSize(2));
        }

        @Test
        void shouldReturnEmptyPageForUnknownUser() {
            assertThat(mockMvcTester.perform(get(BASE_URL + "/users/{userId}", UUID.randomUUID())
                    .with(adminJwt())
                    .param("page", "0")
                    .param("size", "10")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(0))
                    .hasPathSatisfying("$.content", content -> assertThat(content).asInstanceOf(LIST).isEmpty());
        }

        @Test
        void shouldFailWhenUnauthorized() {
            assertThat(mockMvcTester.perform(get(BASE_URL + "/users/{userId}", UUID.randomUUID())
                    .param("page", "0")
                    .param("size", "10")))
                    .hasStatus4xxClientError();
        }
    }

    @Nested
    class GetPaymentsByOrderIdTests {

        @Test
        void shouldReturnOrderPaymentsSorted() {
            UUID orderId = UUID.randomUUID();
            Payment older = createAndSavePayment(UUID.randomUUID(), orderId, PaymentStatus.SUCCESS, "100.00", Instant.now().minus(2, ChronoUnit.DAYS));
            Payment newer = createAndSavePayment(UUID.randomUUID(), orderId, PaymentStatus.SUCCESS, "200.00", Instant.now());

            assertThat(mockMvcTester.perform(get(BASE_URL + "/orders/{orderId}", orderId)
                    .with(adminJwt())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$", list -> assertThat(list).asInstanceOf(LIST).hasSize(2))
                    .hasPathSatisfying("$[0].id", id -> assertThat(id).isEqualTo(older.getId().toString()))
                    .hasPathSatisfying("$[1].id", id -> assertThat(id).isEqualTo(newer.getId().toString()));
        }

        @Test
        void shouldReturnEmptyListForUnknownOrder() {
            assertThat(mockMvcTester.perform(get(BASE_URL + "/orders/{orderId}", UUID.randomUUID())
                    .with(adminJwt())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$", list -> assertThat(list).asInstanceOf(LIST).isEmpty());
        }
    }

    @Nested
    class GetPaymentsByStatusTests {

        @Test
        void shouldReturnPaymentsByStatus() {
            createAndSavePayment(UUID.randomUUID(), UUID.randomUUID(), PaymentStatus.SUCCESS, "150.00", null);
            createAndSavePayment(UUID.randomUUID(), UUID.randomUUID(), PaymentStatus.FAILED, "50.00", null);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/status/{status}", PaymentStatus.SUCCESS)
                    .with(adminJwt())
                    .param("page", "0")
                    .param("size", "10")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying("$.content[0].status", status -> assertThat(status).isEqualTo("SUCCESS"));
        }
    }

    @Nested
    class GetTotalSumForAllTests {

        @Test
        void shouldCalculateTotalSum() {
            Instant now = Instant.now();
            createAndSavePayment(UUID.randomUUID(), UUID.randomUUID(), PaymentStatus.SUCCESS, "100.00", now);
            createAndSavePayment(UUID.randomUUID(), UUID.randomUUID(), PaymentStatus.SUCCESS, "200.00", now);

            Instant dateFrom = now.minus(1, ChronoUnit.DAYS);
            Instant dateTo = now.plus(1, ChronoUnit.DAYS);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/total-sum")
                    .with(adminJwt())
                    .param("from", dateFrom.toString())
                    .param("to", dateTo.toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.totalAmount", amount -> assertThat(amount).isEqualTo(300.0));
        }

        @Test
        void shouldReturnZeroForEmptyPeriod() {
            Instant now = Instant.now();
            createAndSavePayment(UUID.randomUUID(), UUID.randomUUID(), PaymentStatus.SUCCESS, "100.00", now);

            Instant dateFrom = now.plus(10, ChronoUnit.DAYS);
            Instant dateTo = now.plus(20, ChronoUnit.DAYS);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/total-sum")
                    .with(adminJwt())
                    .param("from", dateFrom.toString())
                    .param("to", dateTo.toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.totalAmount", amount -> assertThat(amount).isEqualTo(0));
        }
    }

    @Nested
    class GetTotalSumForUserTests {

        @Test
        void shouldCalculateUserSum() {
            UUID userId = UUID.randomUUID();
            Instant now = Instant.now();

            createAndSavePayment(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "150.00", now);
            createAndSavePayment(UUID.randomUUID(), UUID.randomUUID(), PaymentStatus.SUCCESS, "500.00", now);

            Instant dateFrom = now.minus(1, ChronoUnit.DAYS);
            Instant dateTo = now.plus(1, ChronoUnit.DAYS);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/total-sum/by-user")
                    .with(adminJwt())
                    .param("userId", userId.toString())
                    .param("from", dateFrom.toString())
                    .param("to", dateTo.toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.totalAmount", amount -> assertThat(amount).isEqualTo(150.0));
        }

        @Test
        void shouldReturnZeroForUserWithoutPayments() {
            Instant now = Instant.now();
            Instant dateFrom = now.minus(1, ChronoUnit.DAYS);
            Instant dateTo = now.plus(1, ChronoUnit.DAYS);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/total-sum/by-user")
                    .with(adminJwt())
                    .param("userId", UUID.randomUUID().toString())
                    .param("from", dateFrom.toString())
                    .param("to", dateTo.toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.totalAmount", amount -> assertThat(amount).isEqualTo(0));
        }

        @Test
        void shouldFailOnMissingUserId() {
            assertThat(mockMvcTester.perform(get(BASE_URL + "/total-sum/by-user")
                    .with(adminJwt())))
                    .hasStatus4xxClientError();
        }
    }
}