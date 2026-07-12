package com.labwatch.incident.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.labwatch.contracts.incident.IncidentStatus;
import com.labwatch.contracts.monitoring.ViolationStartedPayload;
import com.labwatch.contracts.policy.Metric;
import com.labwatch.contracts.policy.Severity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncidentTest {

    private static final Instant NOW = Instant.parse("2026-07-12T12:00:00Z");

    private static Incident openIncident() {
        return Incident.open(UUID.randomUUID(), violation("9.4", "8.0"), NOW);
    }

    private static ViolationStartedPayload violation(String measured, String threshold) {
        return new ViolationStartedPayload("chamber-042", Metric.TEMPERATURE,
                new BigDecimal(measured), new BigDecimal(threshold),
                NOW.minusSeconds(180), NOW, Severity.HIGH);
    }

    @Test
    void should_open_incident_with_above_limit_reason() {
        Incident incident = Incident.open(UUID.randomUUID(), violation("9.4", "8.0"), NOW);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getReason()).isEqualTo("TEMPERATURE_ABOVE_LIMIT");
        assertThat(incident.getSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void should_open_incident_with_below_limit_reason() {
        Incident incident = Incident.open(UUID.randomUUID(), violation("1.2", "2.0"), NOW);

        assertThat(incident.getReason()).isEqualTo("TEMPERATURE_BELOW_LIMIT");
    }

    @Test
    void should_acknowledge_open_incident() {
        Incident incident = openIncident();

        incident.acknowledge(NOW.plusSeconds(60));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ACKNOWLEDGED);
        assertThat(incident.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void should_follow_full_lifecycle_to_resolved() {
        Incident incident = openIncident();

        incident.acknowledge(NOW.plusSeconds(60));
        incident.startInvestigation(NOW.plusSeconds(120));
        incident.resolve(NOW.plusSeconds(180));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isEqualTo(NOW.plusSeconds(180));
    }

    @Test
    void should_reject_resolution_of_open_incident() {
        Incident incident = openIncident();

        assertThatThrownBy(() -> incident.resolve(NOW))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("OPEN");
    }

    @Test
    void should_reject_acknowledge_when_already_acknowledged() {
        Incident incident = openIncident();
        incident.acknowledge(NOW);

        assertThatThrownBy(() -> incident.acknowledge(NOW))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void should_reject_resolution_of_already_resolved_incident() {
        Incident incident = openIncident();
        incident.acknowledge(NOW);
        incident.startInvestigation(NOW);
        incident.resolve(NOW);

        assertThatThrownBy(() -> incident.resolve(NOW))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void should_reject_investigation_of_open_incident() {
        Incident incident = openIncident();

        assertThatThrownBy(() -> incident.startInvestigation(NOW))
                .isInstanceOf(InvalidTransitionException.class);
    }
}
