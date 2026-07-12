package com.labwatch.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "monitoring_policies")
public class MonitoringPolicy {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id")
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Metric metric;

    @Column(nullable = false)
    private BigDecimal minimum;

    @Column(nullable = false)
    private BigDecimal maximum;

    @Column(name = "violation_duration_seconds", nullable = false)
    private int violationDurationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MonitoringPolicy() {
    }

    private MonitoringPolicy(UUID id, Device device, Metric metric, BigDecimal minimum, BigDecimal maximum,
                             int violationDurationSeconds, Severity severity, Instant now) {
        this.id = id;
        this.device = device;
        this.metric = metric;
        this.minimum = minimum;
        this.maximum = maximum;
        this.violationDurationSeconds = violationDurationSeconds;
        this.severity = severity;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static MonitoringPolicy create(Device device, Metric metric, BigDecimal minimum, BigDecimal maximum,
                                          int violationDurationSeconds, Severity severity, Instant now) {
        requireValidBounds(minimum, maximum);
        requireValidDuration(violationDurationSeconds);
        return new MonitoringPolicy(UUID.randomUUID(), device, metric, minimum, maximum,
                violationDurationSeconds, severity, now);
    }

    public void update(BigDecimal minimum, BigDecimal maximum, int violationDurationSeconds,
                       Severity severity, Instant now) {
        requireValidBounds(minimum, maximum);
        requireValidDuration(violationDurationSeconds);
        this.minimum = minimum;
        this.maximum = maximum;
        this.violationDurationSeconds = violationDurationSeconds;
        this.severity = severity;
        this.updatedAt = now;
    }

    private static void requireValidBounds(BigDecimal minimum, BigDecimal maximum) {
        if (minimum.compareTo(maximum) >= 0) {
            throw new DomainValidationException(
                    "minimum (%s) must be less than maximum (%s)".formatted(minimum, maximum));
        }
    }

    private static void requireValidDuration(int violationDurationSeconds) {
        if (violationDurationSeconds <= 0) {
            throw new DomainValidationException(
                    "violationDurationSeconds must be positive, was " + violationDurationSeconds);
        }
    }

    public UUID getId() {
        return id;
    }

    public Device getDevice() {
        return device;
    }

    public Metric getMetric() {
        return metric;
    }

    public BigDecimal getMinimum() {
        return minimum;
    }

    public BigDecimal getMaximum() {
        return maximum;
    }

    public int getViolationDurationSeconds() {
        return violationDurationSeconds;
    }

    public Severity getSeverity() {
        return severity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
