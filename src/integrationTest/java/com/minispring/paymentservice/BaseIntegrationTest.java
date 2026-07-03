package com.minispring.paymentservice;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.minispring.paymentservice.messaging.kafka.OutboxRelayScheduler;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:8.0"));

    @ServiceConnection
    static KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    protected static WireMockServer wireMockServer;

    @MockitoBean
    protected JwtDecoder jwtDecoder;

    @MockitoBean
    protected OutboxRelayScheduler outboxRelayScheduler;

    static {
        mongoDBContainer.start();
        kafkaContainer.start();
        System.setProperty("spring.data.mongodb.uri", mongoDBContainer.getReplicaSetUrl() + "?uuidRepresentation=standard");
        System.setProperty("app.liquibase.uri", mongoDBContainer.getReplicaSetUrl() + "?uuidRepresentation=standard");

        wireMockServer = new WireMockServer(9999);
        wireMockServer.start();
        WireMock.configureFor("localhost", 9999);
    }

    @AfterEach
    void resetMocks() {
        wireMockServer.resetAll();
    }
}
