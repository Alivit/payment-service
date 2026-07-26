package com.minispring.paymentservice.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.minispring.paymentservice.BaseIntegrationTest;
import com.minispring.paymentservice.client.impl.PaymentGatewayAdapter;
import com.minispring.paymentservice.dto.request.PaymentProcessRequest;
import com.minispring.paymentservice.model.Payment;
import com.minispring.paymentservice.model.PaymentStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class PaymentGatewayAdapterIT extends BaseIntegrationTest {

    @Autowired
    private PaymentGatewayAdapter adapter;

    private Payment testPayment;
    private PaymentProcessRequest request;

    private static final String EXPECTED_EXACT_URL =
            "/integers/?num=1&min=1&max=100&col=1&base=10&format=plain&rnd=new";

    @DynamicPropertySource
    static void configureGatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("app.external-api.url", () -> "http://localhost:" + wireMockServer.port());
    }

    @BeforeEach
    void setUp() {
        testPayment = new Payment();
        testPayment.setId(UUID.randomUUID());
        testPayment.setOrderId(UUID.randomUUID());

        request = new PaymentProcessRequest(testPayment.getOrderId(), "4242424242424242", "123");
    }

    @Test
    void shouldReturnSuccessOnEvenNumberAndStrictlyMatchQueryParams() {
        WireMock.stubFor(get(urlEqualTo(EXPECTED_EXACT_URL))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/plain")
                        .withStatus(200)
                        .withBody("42\n")));

        PaymentStatus status = adapter.processPayment(testPayment, request);

        assertThat(status).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void shouldReturnFailedOnReadTimeout() {
        WireMock.stubFor(get(urlPathEqualTo("/integers/"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(4000).withBody("42\n")));

        PaymentStatus status = adapter.processPayment(testPayment, request);

        assertThat(status).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void shouldReturnFailedOnOddNumber() {
        WireMock.stubFor(get(urlPathEqualTo("/integers/"))
                .willReturn(aResponse().withStatus(200).withBody("13\n")));

        PaymentStatus status = adapter.processPayment(testPayment, request);

        assertThat(status).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void shouldReturnFailedOnEmptyResponse() {
        WireMock.stubFor(get(urlPathEqualTo("/integers/"))
                .willReturn(aResponse().withStatus(200).withBody("   \n")));

        PaymentStatus status = adapter.processPayment(testPayment, request);

        assertThat(status).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void shouldReturnFailedOnInvalidNumberFormat() {
        WireMock.stubFor(get(urlPathEqualTo("/integers/"))
                .willReturn(aResponse().withStatus(200).withBody("InvalidNumber\n")));

        PaymentStatus status = adapter.processPayment(testPayment, request);

        assertThat(status).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void shouldReturnFailedOnHttpErrors() {
        WireMock.stubFor(
                get(urlPathEqualTo("/integers/")).willReturn(aResponse().withStatus(500)));
        assertThat(adapter.processPayment(testPayment, request)).isEqualTo(PaymentStatus.FAILED);

        WireMock.stubFor(
                get(urlPathEqualTo("/integers/")).willReturn(aResponse().withStatus(429)));
        assertThat(adapter.processPayment(testPayment, request)).isEqualTo(PaymentStatus.FAILED);
    }
}
