package com.labwatch.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MonitoringPolicyTest {

    private static final Instant NOW = Instant.parse("2026-07-12T12:00:00Z");
    private static final Device DEVICE = Device.create("chamber-042", "Cold chamber 42", null, NOW);

    @Test
    void should_create_policy_when_bounds_are_valid() {
        MonitoringPolicy policy = MonitoringPolicy.create(DEVICE, Metric.TEMPERATURE,
                new BigDecimal("2.0"), new BigDecimal("8.0"), 180, Severity.HIGH, NOW);

        assertThat(policy.getId()).isNotNull();
        assertThat(policy.getMetric()).isEqualTo(Metric.TEMPERATURE);
        assertThat(policy.getMinimum()).isEqualByComparingTo("2.0");
        assertThat(policy.getMaximum()).isEqualByComparingTo("8.0");
        assertThat(policy.getViolationDurationSeconds()).isEqualTo(180);
        assertThat(policy.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(policy.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void should_reject_policy_when_minimum_exceeds_maximum() {
        assertThatThrownBy(() -> MonitoringPolicy.create(DEVICE, Metric.TEMPERATURE,
                new BigDecimal("9.0"), new BigDecimal("8.0"), 180, Severity.HIGH, NOW))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("minimum");
    }

    @Test
    void should_reject_policy_when_minimum_equals_maximum() {
        assertThatThrownBy(() -> MonitoringPolicy.create(DEVICE, Metric.TEMPERATURE,
                new BigDecimal("8.0"), new BigDecimal("8.0"), 180, Severity.HIGH, NOW))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void should_reject_policy_when_duration_is_not_positive() {
        assertThatThrownBy(() -> MonitoringPolicy.create(DEVICE, Metric.TEMPERATURE,
                new BigDecimal("2.0"), new BigDecimal("8.0"), 0, Severity.HIGH, NOW))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("violationDurationSeconds");
    }

    @Test
    void should_reject_update_when_bounds_become_invalid() {
        MonitoringPolicy policy = MonitoringPolicy.create(DEVICE, Metric.TEMPERATURE,
                new BigDecimal("2.0"), new BigDecimal("8.0"), 180, Severity.HIGH, NOW);

        assertThatThrownBy(() -> policy.update(new BigDecimal("10.0"), new BigDecimal("5.0"),
                180, Severity.HIGH, NOW))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void should_update_policy_when_values_are_valid() {
        MonitoringPolicy policy = MonitoringPolicy.create(DEVICE, Metric.TEMPERATURE,
                new BigDecimal("2.0"), new BigDecimal("8.0"), 180, Severity.HIGH, NOW);
        Instant later = NOW.plusSeconds(60);

        policy.update(new BigDecimal("1.0"), new BigDecimal("9.0"), 300, Severity.MEDIUM, later);

        assertThat(policy.getMinimum()).isEqualByComparingTo("1.0");
        assertThat(policy.getMaximum()).isEqualByComparingTo("9.0");
        assertThat(policy.getViolationDurationSeconds()).isEqualTo(300);
        assertThat(policy.getSeverity()).isEqualTo(Severity.MEDIUM);
        assertThat(policy.getUpdatedAt()).isEqualTo(later);
    }
}
