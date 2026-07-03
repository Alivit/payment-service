package com.minispring.paymentservice.messaging.kafka.deserializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class JacksonKafkaDeserializerTest {

    @Mock
    private ObjectMapper objectMapper;

    private JacksonKafkaDeserializer<DummyDto> deserializer;

    static class DummyDto {
        public String name;
    }

    @BeforeEach
    void setUp() {
        deserializer = new JacksonKafkaDeserializer<>(objectMapper, DummyDto.class);
    }

    @Test
    void shouldReturnNullWhenDataIsNull() {
        DummyDto result = deserializer.deserialize("test-topic", null);
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenDataIsEmpty() {
        DummyDto result = deserializer.deserialize("test-topic", new byte[0]);
        assertThat(result).isNull();
    }

    @Test
    void shouldDeserializeValidJsonSuccessfully() {
        byte[] validJson = "{\"name\":\"test\"}".getBytes();
        DummyDto expectedDto = new DummyDto();
        expectedDto.name = "test";

        when(objectMapper.readValue(validJson, DummyDto.class)).thenReturn(expectedDto);

        DummyDto result = deserializer.deserialize("test-topic", validJson);
        assertThat(result).isEqualTo(expectedDto);
    }

    @Test
    void shouldThrowSerializationExceptionWhenParsingFails() {
        byte[] invalidJson = "{invalid:json}".getBytes();

        when(objectMapper.readValue(any(byte[].class), eq(DummyDto.class)))
                .thenThrow(new RuntimeException("Parsing failed"));

        assertThatThrownBy(() -> deserializer.deserialize("test-topic", invalidJson))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("Error deserializing JSON message for topic: test-topic");
    }
}
