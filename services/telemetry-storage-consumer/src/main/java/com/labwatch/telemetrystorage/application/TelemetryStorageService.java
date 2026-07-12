package com.labwatch.telemetrystorage.application;

import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.telemetry.DeviceTelemetryPayload;
import com.labwatch.telemetrystorage.domain.TelemetryReading;
import com.labwatch.telemetrystorage.domain.TelemetryReadingRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class TelemetryStorageService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryStorageService.class);

    private final TelemetryReadingRepository readings;
    private final Clock clock;

    public TelemetryStorageService(TelemetryReadingRepository readings, Clock clock) {
        this.readings = readings;
        this.clock = clock;
    }

    /**
     * Idempotent: Kafka delivers at-least-once, so the same event may arrive twice.
     * The exists-check handles the common case; the unique constraint on event_id
     * closes the race between two concurrent deliveries.
     */
    public void store(EventEnvelope<DeviceTelemetryPayload> envelope) {
        if (readings.existsByEventId(envelope.eventId())) {
            log.info("Skipping duplicate telemetry event {} for device {}",
                    envelope.eventId(), envelope.payload().deviceId());
            return;
        }
        try {
            readings.save(TelemetryReading.from(envelope, Instant.now(clock)));
        } catch (DataIntegrityViolationException exception) {
            log.info("Concurrent duplicate telemetry event {} ignored", envelope.eventId());
        }
    }
}
