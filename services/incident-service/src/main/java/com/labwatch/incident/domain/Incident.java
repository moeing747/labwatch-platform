package com.labwatch.incident.domain;

import com.labwatch.contracts.incident.IncidentStatus;
import com.labwatch.contracts.monitoring.ViolationStartedPayload;
import com.labwatch.contracts.policy.Metric;
import com.labwatch.contracts.policy.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Owns the lifecycle state machine: OPEN -> ACKNOWLEDGED -> INVESTIGATING -> RESOLVED.
 * Transitions are strictly linear; anything else throws InvalidTransitionException.
 */
@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    private UUID id;

    /** eventId of the violation-started event this incident was created from (unique: idempotency). */
    @Column(name = "triggering_event_id", nullable = false, unique = true)
    private UUID triggeringEventId;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Metric metric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Column(nullable = false)
    private String reason;

    @Column(name = "measured_value", nullable = false)
    private BigDecimal measuredValue;

    @Column(nullable = false)
    private BigDecimal threshold;

    @Column(name = "violation_started_at", nullable = false)
    private Instant violationStartedAt;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Incident() {
    }

    private Incident(UUID id, UUID triggeringEventId, ViolationStartedPayload violation, String reason, Instant now) {
        this.id = id;
        this.triggeringEventId = triggeringEventId;
        this.deviceId = violation.deviceId();
        this.metric = violation.metric();
        this.severity = violation.severity();
        this.status = IncidentStatus.OPEN;
        this.reason = reason;
        this.measuredValue = violation.measuredValue();
        this.threshold = violation.threshold();
        this.violationStartedAt = violation.violationStartedAt();
        this.detectedAt = violation.detectedAt();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Incident open(UUID triggeringEventId, ViolationStartedPayload violation, Instant now) {
        String direction = violation.measuredValue().compareTo(violation.threshold()) > 0 ? "ABOVE" : "BELOW";
        String reason = "%s_%s_LIMIT".formatted(violation.metric(), direction);
        return new Incident(UUID.randomUUID(), triggeringEventId, violation, reason, now);
    }

    public void acknowledge(Instant now) {
        transition(IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED, now);
    }

    public void startInvestigation(Instant now) {
        transition(IncidentStatus.ACKNOWLEDGED, IncidentStatus.INVESTIGATING, now);
    }

    public void resolve(Instant now) {
        transition(IncidentStatus.INVESTIGATING, IncidentStatus.RESOLVED, now);
        this.resolvedAt = now;
    }

    private void transition(IncidentStatus expected, IncidentStatus target, Instant now) {
        if (status != expected) {
            throw new InvalidTransitionException(
                    "Cannot move incident %s from %s to %s".formatted(id, status, target));
        }
        this.status = target;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTriggeringEventId() {
        return triggeringEventId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Metric getMetric() {
        return metric;
    }

    public Severity getSeverity() {
        return severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public BigDecimal getMeasuredValue() {
        return measuredValue;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public Instant getViolationStartedAt() {
        return violationStartedAt;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
