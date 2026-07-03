package com.minispring.paymentservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.instancio.Select.field;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.minispring.paymentservice.BaseIntegrationTest;
import com.minispring.paymentservice.model.Payment;
import com.minispring.paymentservice.model.PaymentStatus;
import com.minispring.paymentservice.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@AutoConfigureMockMvc
public class AdminPaymentControllerIT extends BaseIntegrationTest {

    @Autowired
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
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
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .jwt(builder -> builder.subject(adminId.toString())
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                        .claim("preferred_username", "admin"));
    }

    private RequestPostProcessor userJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(builder -> builder.subject(UUID.randomUUID().toString())
                        .claim("realm_access", Map.of("roles", List.of("USER")))
                        .claim("preferred_username", "regular_user"));
    }

    private Payment createAndSavePayment(
            UUID userId, UUID orderId, PaymentStatus status, String amount, Instant createdAt) {
        Payment payment = Instancio.of(Payment.class)
                .ignore(field(Payment::getId))
                .set(field(Payment::getUserId), userId)
                .set(field(Payment::getOrderId), orderId)
                .set(field(Payment::getPaymentStatus), status)
                .set(field(Payment::getPaymentAmount), new BigDecimal(amount))
                .set(field(Payment::getCreatedAt), createdAt != null ? createdAt : Instant.now())
                .ignore(field(Payment::getVersion))
                .create();
        return paymentRepository.save(payment);
    }

    @Nested
    class GetPaymentsByUserIdTests {

        @Test
        void shouldReturnUserPayments() {
            UUID userId = UUID.randomUUID();
            createAndSavePayment(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "150.00", null);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/users")
                            .with(adminJwt())
                            .param("userId", userId.toString())
                            .param("page", "0")
                            .param("size", "10")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying(
                            "$.content[0].userId", id -> assertThat(id).isEqualTo(userId.toString()));
        }

        @Test
        void shouldApplyPagination() {
            UUID userId = UUID.randomUUID();
            createAndSavePayment(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "10.00", null);
            createAndSavePayment(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "20.00", null);
            createAndSavePayment(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "30.00", null);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/users")
                            .with(adminJwt())
                            .param("userId", userId.toString())
                            .param("page", "0")
                            .param("size", "2")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> assertThat(total).isEqualTo(3))
                    .hasPathSatisfying(
                            "$.content",
                            content -> assertThat(content).asInstanceOf(LIST).hasSize(2));
        }

        @Test
        void shouldReturnEmptyPageForUnknownUser() {
            assertThat(mockMvcTester.perform(get(BASE_URL + "/users")
                            .with(adminJwt())
                            .param("userId", UUID.randomUUID().toString())
                            .param("page", "0")
                            .param("size", "10")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> assertThat(total).isEqualTo(0))
                    .hasPathSatisfying(
                            "$.content",
                            content -> assertThat(content).asInstanceOf(LIST).isEmpty());
        }

        @Test
        void shouldWorkWithDefaultPaginationWhenPageAndSizeAreOmitted() {
            UUID userId = UUID.randomUUID();
            createAndSavePayment(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "150.00", null);

            assertThat(mockMvcTester.perform(
                            get(BASE_URL + "/users").with(adminJwt()).param("userId", userId.toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.page.size", size -> assertThat(size).isEqualTo(20))
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> assertThat(total).isEqualTo(1));
        }

        @Test
        void shouldFailWhenUnauthorized() {
            assertThat(mockMvcTester.perform(get(BASE_URL + "/users")
                            .param("userId", UUID.randomUUID().toString())))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void shouldFailWhenUserHasNoAdminRole() {
            assertThat(mockMvcTester.perform(get(BASE_URL + "/users")
                            .with(userJwt())
                            .param("userId", UUID.randomUUID().toString())))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class GetPaymentsByOrderIdTests {

        @Test
        void shouldReturnSingleOrderPayment() {
            UUID orderId = UUID.randomUUID();
            Payment savedPayment =
                    createAndSavePayment(UUID.randomUUID(), orderId, PaymentStatus.SUCCESS, "100.00", Instant.now());

            assertThat(mockMvcTester.perform(
                            get(BASE_URL + "/orders").with(adminJwt()).param("orderId", orderId.toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id)
                            .isEqualTo(savedPayment.getId().toString()))
                    .hasPathSatisfying("$.status", status -> assertThat(status).isEqualTo("SUCCESS"));
        }

        @Test
        void shouldFailForUnknownOrder() {
            assertThat(mockMvcTester.perform(get(BASE_URL + "/orders")
                            .with(adminJwt())
                            .param("orderId", UUID.randomUUID().toString())))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class GetPaymentsByStatusTests {

        @Test
        void shouldReturnPaymentsByStatus() {
            createAndSavePayment(UUID.randomUUID(), UUID.randomUUID(), PaymentStatus.SUCCESS, "150.00", null);
            createAndSavePayment(UUID.randomUUID(), UUID.randomUUID(), PaymentStatus.FAILED, "50.00", null);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/status")
                            .with(adminJwt())
                            .param("status", PaymentStatus.SUCCESS.name())
                            .param("page", "0")
                            .param("size", "10")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying(
                            "$.content[0].status", status -> assertThat(status).isEqualTo("SUCCESS"));
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
                    .hasPathSatisfying(
                            "$.totalSum", amount -> assertThat(amount).isEqualTo(300.0));
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
                    .hasPathSatisfying(
                            "$.totalSum", amount -> assertThat(amount).isEqualTo(0));
        }

        @Test
        void shouldCalculateTotalSumWithDefaultDatesWhenParamsOmitted() {
            Instant now = Instant.now();
            createAndSavePayment(UUID.randomUUID(), UUID.randomUUID(), PaymentStatus.SUCCESS, "100.00", now);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/total-sum").with(adminJwt())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.totalSum", amount -> assertThat(amount).isEqualTo(100.0));
        }

        @Test
        void shouldReturnBadRequestWhenFromDateIsAfterToDate() {
            Instant now = Instant.now();
            Instant dateFrom = now.plus(1, ChronoUnit.DAYS);
            Instant dateTo = now.minus(1, ChronoUnit.DAYS);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/total-sum")
                            .with(adminJwt())
                            .param("from", dateFrom.toString())
                            .param("to", dateTo.toString())))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.error", code -> assertThat(code).isEqualTo("VALIDATION_FAILED"))
                    .hasPathSatisfying(
                            "$.message", msg -> assertThat(msg).asString().contains("failed validation"));
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
                    .hasPathSatisfying(
                            "$.totalSum", amount -> assertThat(amount).isEqualTo(150.0));
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
                    .hasPathSatisfying(
                            "$.totalSum", amount -> assertThat(amount).isEqualTo(0));
        }

        @Test
        void shouldFailOnMissingUserId() {
            assertThat(mockMvcTester.perform(
                            get(BASE_URL + "/total-sum/by-user").with(adminJwt())))
                    .hasStatus4xxClientError();
        }
    }

    @Nested
    class RequestValidationTests {

        @ParameterizedTest
        @CsvFileSource(resources = "/testdata/admin/validation-invalid-formats.csv", numLinesToSkip = 1)
        void shouldReturnBadRequestForInvalidParameterFormats(String route, String paramName, String invalidValue) {
            assertThat(mockMvcTester.perform(
                            get(BASE_URL + route).with(adminJwt()).param(paramName, invalidValue)))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.error", value -> assertThat(value)
                            .asString()
                            .isIn("INVALID_PARAMETER_FORMAT", "INVALID_ARGUMENT", "VALIDATION_FAILED"));
        }

        @ParameterizedTest
        @CsvFileSource(resources = "/testdata/admin/validation-missing-params.csv", numLinesToSkip = 1)
        void shouldReturnBadRequestForMissingParameters(String route) {
            assertThat(mockMvcTester.perform(get(BASE_URL + route).with(adminJwt())))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.error", value -> assertThat(value).isEqualTo("MISSING_PARAMETER"));
        }

        @ParameterizedTest()
        @CsvFileSource(resources = "/testdata/admin/validation-empty-params.csv", numLinesToSkip = 1)
        void shouldReturnBadRequestForEmptyParameters(String route, String emptyParam) {
            assertThat(mockMvcTester.perform(
                            get(BASE_URL + route).with(adminJwt()).param(emptyParam, "")))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.error", value -> assertThat(value)
                            .asString()
                            .isIn("INVALID_PARAMETER_FORMAT", "MISSING_PARAMETER"));
        }
    }

    @Nested
    class SecurityAuthorizationTests {

        @ParameterizedTest()
        @CsvFileSource(resources = "/testdata/admin/admin-security-routes.csv", numLinesToSkip = 1)
        void shouldDenyAccessToAllAdminRoutesForRegularUser(String route) {
            assertThat(mockMvcTester.perform(get(BASE_URL + route).with(userJwt())))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @ParameterizedTest()
        @CsvFileSource(resources = "/testdata/admin/admin-security-routes.csv", numLinesToSkip = 1)
        void shouldDenyAccessToAllAdminRoutesWithoutToken(String route) {
            assertThat(mockMvcTester.perform(get(BASE_URL + route))).hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }
}
