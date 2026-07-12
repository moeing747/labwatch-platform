package com.labwatch.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.labwatch.asset.PostgresContainerSupport;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DeviceRepositoryIT extends PostgresContainerSupport {

    private static final Instant NOW = Instant.parse("2026-07-12T12:00:00Z");

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private MonitoringPolicyRepository policies;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void should_persist_and_load_device_by_business_id() {
        devices.saveAndFlush(Device.create("repo-chamber-042", "Cold chamber 42", null, NOW));
        entityManager.clear();

        assertThat(devices.findByDeviceId("repo-chamber-042"))
                .hasValueSatisfying(device -> {
                    assertThat(device.getName()).isEqualTo("Cold chamber 42");
                    assertThat(device.getCreatedAt()).isEqualTo(NOW);
                });
    }

    @Test
    void should_reject_duplicate_device_id_via_unique_constraint() {
        devices.saveAndFlush(Device.create("repo-chamber-001", "First", null, NOW));

        assertThatThrownBy(() ->
                devices.saveAndFlush(Device.create("repo-chamber-001", "Second", null, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_persist_policy_with_enums_and_decimals_intact() {
        Device device = devices.saveAndFlush(Device.create("repo-chamber-002", "Chamber", null, NOW));
        MonitoringPolicy saved = policies.saveAndFlush(MonitoringPolicy.create(device, Metric.TEMPERATURE,
                new BigDecimal("2.00"), new BigDecimal("8.00"), 180, Severity.HIGH, NOW));
        entityManager.clear();

        assertThat(policies.findById(saved.getId())).hasValueSatisfying(policy -> {
            assertThat(policy.getMetric()).isEqualTo(Metric.TEMPERATURE);
            assertThat(policy.getSeverity()).isEqualTo(Severity.HIGH);
            assertThat(policy.getMinimum()).isEqualByComparingTo("2.00");
            assertThat(policy.getMaximum()).isEqualByComparingTo("8.00");
            assertThat(policy.getDevice().getDeviceId()).isEqualTo("repo-chamber-002");
        });
    }

    @Test
    void should_reject_second_policy_for_same_device_and_metric() {
        Device device = devices.saveAndFlush(Device.create("repo-chamber-003", "Chamber", null, NOW));
        policies.saveAndFlush(MonitoringPolicy.create(device, Metric.TEMPERATURE,
                new BigDecimal("2.0"), new BigDecimal("8.0"), 180, Severity.HIGH, NOW));

        assertThatThrownBy(() -> policies.saveAndFlush(MonitoringPolicy.create(device, Metric.TEMPERATURE,
                new BigDecimal("1.0"), new BigDecimal("9.0"), 60, Severity.LOW, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
