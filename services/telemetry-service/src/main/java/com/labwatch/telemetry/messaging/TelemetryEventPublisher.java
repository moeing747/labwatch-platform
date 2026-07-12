package com.labwatch.telemetry.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.Topics;
import com.labwatch.contracts.telemetry.DeviceTelemetryPayload;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TelemetryEventPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Timer publishTimer;

    public TelemetryEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                   MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.publishTimer = Timer.builder("labwatch.telemetry.publish.duration")
                .description("Time spent publishing a telemetry event to Kafka")
                .register(meterRegistry);
    }

    public void publish(EventEnvelope<DeviceTelemetryPayload> envelope) {
        long start = System.nanoTime();
        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(Topics.DEVICE_TELEMETRY_V1, envelope.payload().deviceId(), json)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            publishTimer.record(Duration.ofNanos(System.nanoTime() - start));
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
