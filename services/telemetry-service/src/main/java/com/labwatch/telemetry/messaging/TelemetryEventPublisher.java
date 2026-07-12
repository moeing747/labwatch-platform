package com.labwatch.telemetry.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.Topics;
import com.labwatch.contracts.telemetry.DeviceTelemetryPayload;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TelemetryEventPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public TelemetryEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(EventEnvelope<DeviceTelemetryPayload> envelope) {
        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(Topics.DEVICE_TELEMETRY_V1, envelope.payload().deviceId(), json)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (JsonProcessingException exception) {
            throw new EventPublishException("Failed to serialize telemetry event", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("Interrupted while publishing telemetry event", exception);
        } catch (Exception exception) {
            throw new EventPublishException("Failed to publish telemetry event", exception);
        }
    }
}
