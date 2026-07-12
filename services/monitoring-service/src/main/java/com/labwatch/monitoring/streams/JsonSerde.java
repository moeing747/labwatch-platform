package com.labwatch.monitoring.streams;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/**
 * JSON serde for Kafka Streams. Deserialization failures throw, which the
 * configured LogAndContinueExceptionHandler turns into "skip the record".
 */
public class JsonSerde<T> implements Serde<T> {

    private final ObjectMapper objectMapper;
    private final TypeReference<T> type;

    public JsonSerde(ObjectMapper objectMapper, TypeReference<T> type) {
        this.objectMapper = objectMapper;
        this.type = type;
    }

    @Override
    public Serializer<T> serializer() {
        return (topic, data) -> {
            try {
                return data == null ? null : objectMapper.writeValueAsBytes(data);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        };
    }

    @Override
    public Deserializer<T> deserializer() {
        return (topic, bytes) -> {
            try {
                return bytes == null ? null : objectMapper.readValue(bytes, type);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        };
    }
}
