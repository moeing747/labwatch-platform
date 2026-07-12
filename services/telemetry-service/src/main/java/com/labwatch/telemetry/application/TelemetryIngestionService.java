package com.labwatch.telemetry.application;

import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.EventTypes;
import com.labwatch.contracts.telemetry.DeviceTelemetryPayload;
import com.labwatch.telemetry.api.TelemetryDtos.TelemetryRequest;
import com.labwatch.telemetry.messaging.TelemetryEventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TelemetryIngestionService {

    /** Tolerated clock skew for device timestamps that are slightly ahead of server time. */
    private static final Duration MAX_CLOCK_SKEW = Duration.ofSeconds(30);
    private static final String PRODUCER_NAME = "telemetry-service";

    private final TelemetryEventPublisher publisher;
    private final Counter receivedCounter;
    private final Clock clock;

    public TelemetryIngestionService(TelemetryEventPublisher publisher, MeterRegistry meterRegistry, Clock clock) {
        this.publisher = publisher;
        this.receivedCounter = Counter.builder("labwatch.telemetry.received")
                .description("Telemetry readings accepted and published")
                .register(meterRegistry);
        this.clock = clock;
    }

    public EventEnvelope<DeviceTelemetryPayload> ingest(TelemetryRequest request, UUID correlationId) {
        Instant now = Instant.now(clock);
        if (request.timestamp().isAfter(now.plus(MAX_CLOCK_SKEW))) {
            throw new InvalidTelemetryException(
                    "timestamp %s is in the future (server time %s)".formatted(request.timestamp(), now));
        }

        DeviceTelemetryPayload payload = new DeviceTelemetryPayload(
                request.deviceId(),
                normalize(request.temperature()),
                normalize(request.humidity()),
                request.operatingState());

        EventEnvelope<DeviceTelemetryPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                EventTypes.DEVICE_TELEMETRY_RECEIVED,
                1,
                request.timestamp(),
                now,
                correlationId,
                PRODUCER_NAME,
                payload);

        publisher.publish(envelope);
        receivedCounter.increment();
        return envelope;
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
