package com.minispring.paymentservice.messaging.kafka.deserializer;

import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public class JacksonKafkaDeserializer<T> implements Deserializer<T> {

    private final ObjectMapper objectMapper;
    private final Class<T> targetType;

    public JacksonKafkaDeserializer(ObjectMapper objectMapper, Class<T> targetType) {
        this.objectMapper = objectMapper;
        this.targetType = targetType;
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        return deserialize(topic, null, data);
    }

    @Override
    public T deserialize(String topic, Headers headers, byte[] data) {
        if (data == null || data.length == 0) {
            log.debug("Received empty payload for topic [{}]", topic);
            return null;
        }

        try {
            return objectMapper.readValue(data, targetType);

        } catch (Exception e) {
            String rawPayload = new String(data, StandardCharsets.UTF_8);
            log.error("Failed to parse JSON from topic [{}]. Raw payload: {}", topic, rawPayload, e);
            throw new SerializationException("Error deserializing JSON message for topic: " + topic, e);
        }
    }
}
