package com.labwatch.telemetrystorage.domain;

import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.telemetry.DeviceOperatingState;
import com.labwatch.contracts.telemetry.DeviceTelemetryPayload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telemetry_readings")
public class TelemetryReading {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private BigDecimal temperature;

    @Column(nullable = false)
    private BigDecimal humidity;

    @Enumerated(EnumType.STRING)
    @Column(name = "operating_state", nullable = false)
    private DeviceOperatingState operatingState;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    protected TelemetryReading() {
    }

    private TelemetryReading(UUID id, UUID eventId, String deviceId, BigDecimal temperature, BigDecimal humidity,
                             DeviceOperatingState operatingState, Instant occurredAt, Instant recordedAt,
                             UUID correlationId) {
        this.id = id;
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.temperature = temperature;
        this.humidity = humidity;
        this.operatingState = operatingState;
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
        this.correlationId = correlationId;
    }

    public static TelemetryReading from(EventEnvelope<DeviceTelemetryPayload> envelope, Instant recordedAt) {
        DeviceTelemetryPayload payload = envelope.payload();
        return new TelemetryReading(UUID.randomUUID(), envelope.eventId(), payload.deviceId(),
                payload.temperature(), payload.humidity(), payload.operatingState(),
                envelope.occurredAt(), recordedAt, envelope.correlationId());
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public BigDecimal getHumidity() {
        return humidity;
    }

    public DeviceOperatingState getOperatingState() {
        return operatingState;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }
}
