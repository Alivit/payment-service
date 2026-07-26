package com.minispring.paymentservice.messaging.kafka.serializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class JacksonKafkaSerializerTest {

    @Mock
    private ObjectMapper objectMapper;

    private JacksonKafkaSerializer<Object> serializer;

    @BeforeEach
    void setUp() {
        serializer = new JacksonKafkaSerializer<>(objectMapper);
    }

    @Test
    void shouldReturnNullWhenDataIsNull() {
        byte[] result = serializer.serialize("test-topic", null);
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnStringBytesWhenDataIsString() {
        String data = "hello kafka";
        byte[] result = serializer.serialize("test-topic", data);
        assertThat(result).isEqualTo(data.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldReturnSameBytesWhenDataIsByteArray() {
        byte[] data = new byte[] {1, 2, 3};
        byte[] result = serializer.serialize("test-topic", data);
        assertThat(result).isEqualTo(data);
    }

    @Test
    void shouldSerializeObjectUsingObjectMapper() {
        Object data = new Object();
        byte[] expectedBytes = "{\"key\":\"value\"}".getBytes();

        when(objectMapper.writeValueAsBytes(data)).thenReturn(expectedBytes);

        byte[] result = serializer.serialize("test-topic", data);
        assertThat(result).isEqualTo(expectedBytes);
    }

    @Test
    void shouldThrowSerializationExceptionWhenObjectMapperFails() {
        Object data = new Object();

        when(objectMapper.writeValueAsBytes(any())).thenThrow(new RuntimeException("JSON error"));

        assertThatThrownBy(() -> serializer.serialize("test-topic", data))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("Error serializing JSON message for topic: test-topic");
    }
}
