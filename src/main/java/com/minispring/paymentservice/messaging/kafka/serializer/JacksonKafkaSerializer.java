package com.minispring.paymentservice.messaging.kafka.serializer;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import tools.jackson.databind.ObjectMapper;

public class JacksonKafkaSerializer<T> implements Serializer<T> {

    private final ObjectMapper objectMapper;

    public JacksonKafkaSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(String topic, T data) {
        if (data == null) {
            return null;
        }
        try {

            if (data instanceof String str) {
                return str.getBytes(StandardCharsets.UTF_8);
            }

            if (data instanceof byte[] bytes) {
                return bytes;
            }

            return objectMapper.writeValueAsBytes(data);

        } catch (Exception e) {
            throw new SerializationException("Error serializing JSON message for topic: " + topic, e);
        }
    }
}
