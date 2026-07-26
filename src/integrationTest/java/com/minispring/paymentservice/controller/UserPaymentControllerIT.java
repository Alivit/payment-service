package com.minispring.paymentservice.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.instancio.Select.field;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.minispring.paymentservice.BaseIntegrationTest;
import com.minispring.paymentservice.dto.request.PaymentProcessRequest;
import com.minispring.paymentservice.model.Payment;
import com.minispring.paymentservice.model.PaymentStatus;
import com.minispring.paymentservice.repository.OutboxRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
public class UserPaymentControllerIT extends BaseIntegrationTest {

    @Autowired
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private MockMvcTester mockMvcTester;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private JsonMapper jsonMapper;

    private static final String BASE_URL = "/api/v1/payments";

    private UUID testUserId;
    private UUID otherUserId;
    private UUID testOrderId;

    @BeforeEach
    public void init() {
        testUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        testOrderId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        paymentRepository.deleteAll();
        outboxRepository.deleteAll();
    }

    private RequestPostProcessor userJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(builder -> builder.subject(testUserId.toString())
                        .claim("realm_access", Map.of("roles", List.of("USER")))
                        .claim("preferred_username", "test-user"));
    }

    private RequestPostProcessor otherUserJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(builder -> builder.subject(otherUserId.toString())
                        .claim("realm_access", Map.of("roles", List.of("USER")))
                        .claim("preferred_username", "other-user"));
    }

    private Payment createAndSavePayment(UUID userId, UUID orderId, PaymentStatus status, String amount) {
        Payment payment = Instancio.of(Payment.class)
                .ignore(field(Payment::getId))
                .set(field(Payment::getUserId), userId)
                .set(field(Payment::getOrderId), orderId)
                .set(field(Payment::getPaymentStatus), status)
                .set(field(Payment::getPaymentAmount), new BigDecimal(amount))
                .set(field(Payment::getCreatedAt), Instant.now())
                .ignore(field(Payment::getVersion))
                .create();
        return paymentRepository.save(payment);
    }

    @Nested
    class CreatePaymentTests {

        private PaymentProcessRequest createDto;

        @BeforeEach
        public void initCreate() {
            createDto = new PaymentProcessRequest(testOrderId, "4242424242424242", "123");
        }

        @Test
        void shouldCreatePaymentSuccessfully() {
            createAndSavePayment(testUserId, testOrderId, PaymentStatus.PENDING, "150.00");

            WireMock.stubFor(
                    WireMock.get(urlEqualTo("/integers/?num=1&min=1&max=100&col=1&base=10&format=plain&rnd=new"))
                            .willReturn(aResponse().withStatus(200).withBody("42\n")));

            assertThat(mockMvcTester.perform(post(BASE_URL)
                            .with(userJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(createDto))))
                    .hasStatus(HttpStatus.CREATED);

            assertThat(outboxRepository.findAll()).isNotEmpty();
        }

        @Test
        void shouldDenyAccessIfPayingForOtherUserOrder() {
            createAndSavePayment(otherUserId, testOrderId, PaymentStatus.PENDING, "150.00");

            assertThat(mockMvcTester.perform(post(BASE_URL)
                            .with(userJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(createDto))))
                    .hasStatus4xxClientError();
        }

        @Test
        void shouldReturnExistingPaymentIfAlreadySuccessful() {
            createAndSavePayment(testUserId, testOrderId, PaymentStatus.SUCCESS, "150.00");

            assertThat(mockMvcTester.perform(post(BASE_URL)
                            .with(userJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(createDto))))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson()
                    .hasPathSatisfying("$.status", status -> assertThat(status).isEqualTo("SUCCESS"));

            assertThat(outboxRepository.findAll()).isEmpty();
        }
    }

    @Nested
    class GetMyPaymentsTests {

        @Test
        void shouldReturnUserPayments() {
            createAndSavePayment(testUserId, UUID.randomUUID(), PaymentStatus.SUCCESS, "100.00");

            assertThat(mockMvcTester.perform(
                            get(BASE_URL).with(userJwt()).param("page", "0").param("size", "10")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying(
                            "$.content[0].userId", id -> assertThat(id).isEqualTo(testUserId.toString()));
        }

        @Test
        void shouldExcludeOtherUserPayments() {
            createAndSavePayment(testUserId, UUID.randomUUID(), PaymentStatus.SUCCESS, "100.00");
            createAndSavePayment(otherUserId, UUID.randomUUID(), PaymentStatus.SUCCESS, "200.00");

            assertThat(mockMvcTester.perform(
                            get(BASE_URL).with(userJwt()).param("page", "0").param("size", "10")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> assertThat(total).isEqualTo(1));
        }

        @Test
        void shouldReturnEmptyPage() {
            assertThat(mockMvcTester.perform(
                            get(BASE_URL).with(userJwt()).param("page", "0").param("size", "10")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> assertThat(total).isEqualTo(0));
        }

        @Test
        void shouldApplyPagination() {
            createAndSavePayment(testUserId, UUID.randomUUID(), PaymentStatus.SUCCESS, "10.00");
            createAndSavePayment(testUserId, UUID.randomUUID(), PaymentStatus.SUCCESS, "20.00");
            createAndSavePayment(testUserId, UUID.randomUUID(), PaymentStatus.SUCCESS, "30.00");

            assertThat(mockMvcTester.perform(
                            get(BASE_URL).with(userJwt()).param("page", "0").param("size", "2")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> assertThat(total).isEqualTo(3))
                    .hasPathSatisfying(
                            "$.content",
                            content -> assertThat(content).asInstanceOf(LIST).hasSize(2));
        }
    }

    @Nested
    class GetPaymentsByOrderIdTests {

        @Test
        void shouldReturnSingleOrderPayment() {
            Payment savedPayment = createAndSavePayment(testUserId, testOrderId, PaymentStatus.SUCCESS, "150.00");

            assertThat(mockMvcTester.perform(
                            get(BASE_URL + "/orders").with(userJwt()).param("orderId", testOrderId.toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id)
                            .isEqualTo(savedPayment.getId().toString()));
        }

        @Test
        void shouldReturnNotFoundForNonExistentOrder() {
            UUID nonExistentOrderId = UUID.randomUUID();

            assertThat(mockMvcTester.perform(
                            get(BASE_URL + "/orders").with(userJwt()).param("orderId", nonExistentOrderId.toString())))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void shouldDenyAccessToOtherUserOrder() {
            createAndSavePayment(testUserId, testOrderId, PaymentStatus.SUCCESS, "150.00");

            assertThat(mockMvcTester.perform(
                            get(BASE_URL + "/orders").with(otherUserJwt()).param("orderId", testOrderId.toString())))
                    .hasStatus4xxClientError();
        }
    }

    @Nested
    class GetMyPaymentsByStatusTests {

        @Test
        void shouldReturnPaymentsWithStatus() {
            createAndSavePayment(testUserId, UUID.randomUUID(), PaymentStatus.PENDING, "50.00");
            createAndSavePayment(testUserId, UUID.randomUUID(), PaymentStatus.SUCCESS, "100.00");

            assertThat(mockMvcTester.perform(get(BASE_URL + "/status")
                            .with(userJwt())
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

        @Test
        void shouldReturnEmptyPageForStatus() {
            createAndSavePayment(testUserId, UUID.randomUUID(), PaymentStatus.SUCCESS, "100.00");

            assertThat(mockMvcTester.perform(get(BASE_URL + "/status")
                            .with(userJwt())
                            .param("status", PaymentStatus.FAILED.name())
                            .param("page", "0")
                            .param("size", "10")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> assertThat(total).isEqualTo(0));
        }
    }

    @Nested
    class GetMyTotalSumTests {

        @Test
        void shouldCalculateSum() {
            createAndSavePayment(testUserId, UUID.randomUUID(), PaymentStatus.SUCCESS, "50.00");
            createAndSavePayment(testUserId, UUID.randomUUID(), PaymentStatus.SUCCESS, "150.00");

            Instant dateFrom = Instant.now().minus(1, ChronoUnit.DAYS);
            Instant dateTo = Instant.now().plus(1, ChronoUnit.DAYS);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/total-sum")
                            .with(userJwt())
                            .param("from", dateFrom.toString())
                            .param("to", dateTo.toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.totalSum", amount -> assertThat(amount).isEqualTo(200.0));
        }

        @Test
        void shouldIgnoreOtherUserSum() {
            createAndSavePayment(testUserId, UUID.randomUUID(), PaymentStatus.SUCCESS, "50.00");
            createAndSavePayment(otherUserId, UUID.randomUUID(), PaymentStatus.SUCCESS, "1000.00");

            Instant dateFrom = Instant.now().minus(1, ChronoUnit.DAYS);
            Instant dateTo = Instant.now().plus(1, ChronoUnit.DAYS);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/total-sum")
                            .with(userJwt())
                            .param("from", dateFrom.toString())
                            .param("to", dateTo.toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.totalSum", amount -> assertThat(amount).isEqualTo(50.0));
        }

        @Test
        void shouldReturnZeroForEmptyPeriod() {
            createAndSavePayment(testUserId, UUID.randomUUID(), PaymentStatus.SUCCESS, "50.00");

            Instant dateFrom = Instant.now().plus(10, ChronoUnit.DAYS);
            Instant dateTo = Instant.now().plus(20, ChronoUnit.DAYS);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/total-sum")
                            .with(userJwt())
                            .param("from", dateFrom.toString())
                            .param("to", dateTo.toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.totalSum", amount -> assertThat(amount).isEqualTo(0));
        }

        @Test
        void shouldCalculateSumOnlyForSuccessPayments() {
            createAndSavePayment(testUserId, UUID.randomUUID(), PaymentStatus.SUCCESS, "50.00");
            createAndSavePayment(testUserId, UUID.randomUUID(), PaymentStatus.SUCCESS, "150.00");
            createAndSavePayment(testUserId, UUID.randomUUID(), PaymentStatus.FAILED, "1000.00");
            createAndSavePayment(testUserId, UUID.randomUUID(), PaymentStatus.PENDING, "500.00");

            Instant dateFrom = Instant.now().minus(1, ChronoUnit.DAYS);
            Instant dateTo = Instant.now().plus(1, ChronoUnit.DAYS);

            assertThat(mockMvcTester.perform(get(BASE_URL + "/total-sum")
                            .with(userJwt())
                            .param("from", dateFrom.toString())
                            .param("to", dateTo.toString())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.totalSum", amount -> assertThat(amount).isEqualTo(200.0));
        }
    }

    @Nested
    class SecurityAuthorizationTests {

        @ParameterizedTest
        @CsvFileSource(resources = "/testdata/user/user-security-routes.csv", numLinesToSkip = 1)
        void shouldDenyAccessWithoutToken(String method, String route) {
            assertThat(mockMvcTester.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                                            org.springframework.http.HttpMethod.valueOf(method), BASE_URL + route)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}")))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class RequestValidationTests {

        @ParameterizedTest
        @CsvFileSource(resources = "/testdata/user/user-invalid-formats.csv", numLinesToSkip = 1)
        void shouldReturnBadRequestForInvalidParameterFormats(String route, String paramName, String invalidValue) {
            assertThat(mockMvcTester.perform(
                            get(BASE_URL + route).with(userJwt()).param(paramName, invalidValue)))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.error", error -> assertThat(error)
                            .asString()
                            .isIn("INVALID_PARAMETER_FORMAT", "VALIDATION_FAILED", "MISSING_PARAMETER"));
        }

        @ParameterizedTest
        @CsvFileSource(resources = "/testdata/user/user-missing-params.csv", numLinesToSkip = 1)
        void shouldReturnBadRequestForMissingParameters(String route) {
            assertThat(mockMvcTester.perform(get(BASE_URL + route).with(userJwt())))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.error", error -> assertThat(error).asString().isEqualTo("MISSING_PARAMETER"));
        }

        @ParameterizedTest
        @CsvFileSource(resources = "/testdata/user/user-empty-params.csv", numLinesToSkip = 1)
        void shouldReturnBadRequestForEmptyParameters(String route, String emptyParam) {
            assertThat(mockMvcTester.perform(
                            get(BASE_URL + route).with(userJwt()).param(emptyParam, "")))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.error", error -> assertThat(error)
                            .asString()
                            .isIn("INVALID_PARAMETER_FORMAT", "VALIDATION_FAILED", "MISSING_PARAMETER"));
        }

        @ParameterizedTest
        @CsvFileSource(resources = "/testdata/user/user-invalid-body.csv", numLinesToSkip = 1)
        void shouldFailWhenDtoIsInvalid(String orderIdStr, String cardNumber, String cvv, String expectedErrorField) {
            UUID orderId = "null".equals(orderIdStr) ? null : UUID.fromString(orderIdStr);
            PaymentProcessRequest invalidDto = new PaymentProcessRequest(orderId, cardNumber, cvv);

            assertThat(mockMvcTester.perform(post(BASE_URL)
                            .with(userJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(invalidDto))))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.error", error -> assertThat(error).asString().isEqualTo("VALIDATION_FAILED"))
                    .hasPathSatisfying("$.details", details -> assertThat(details)
                            .asList()
                            .anySatisfy(item -> assertThat(item.toString()).contains(expectedErrorField)));
        }
    }
}
