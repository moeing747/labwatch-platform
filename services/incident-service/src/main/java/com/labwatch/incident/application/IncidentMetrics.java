package com.labwatch.incident.application;

import com.labwatch.contracts.incident.IncidentStatus;
import com.labwatch.incident.domain.Incident;
import com.labwatch.incident.domain.IncidentRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Incident metrics per docs/observability.md. Counters are incremented inside
 * the surrounding transaction, so a rolled-back transaction still counts - an
 * accepted imprecision for operational metrics. The open-incidents gauge reads
 * the database on every scrape, so it is exact.
 */
@Component
public class IncidentMetrics {

    private final MeterRegistry registry;
    private final Timer resolutionDuration;

    public IncidentMetrics(MeterRegistry registry, IncidentRepository incidents) {
        this.registry = registry;
        this.resolutionDuration = Timer.builder("labwatch.incident.resolution.duration")
                .description("Time from incident creation to resolution")
                .register(registry);
        Gauge.builder("labwatch.incidents.open.current", incidents,
                        repository -> repository.countByStatusNot(IncidentStatus.RESOLVED))
                .description("Incidents not yet resolved")
                .register(registry);
    }

    public void recordOpened(Incident incident) {
        registry.counter("labwatch.incidents.opened",
                "severity", incident.getSeverity().name(), "reason", incident.getReason()).increment();
    }

    public void recordResolved(Incident incident, Instant resolvedAt) {
        registry.counter("labwatch.incidents.resolved",
                "severity", incident.getSeverity().name()).increment();
        resolutionDuration.record(Duration.between(incident.getCreatedAt(), resolvedAt));
    }
}
