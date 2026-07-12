package com.labwatch.incident.application;

import com.labwatch.contracts.EventTypes;
import com.labwatch.contracts.incident.IncidentStatus;
import com.labwatch.incident.domain.HistoryAction;
import com.labwatch.incident.domain.Incident;
import com.labwatch.incident.domain.IncidentHistoryEntry;
import com.labwatch.incident.domain.IncidentHistoryRepository;
import com.labwatch.incident.domain.IncidentRepository;
import com.labwatch.incident.domain.ResourceNotFoundException;
import com.labwatch.incident.messaging.OutboxWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IncidentService {

    private final IncidentRepository incidents;
    private final IncidentHistoryRepository history;
    private final OutboxWriter publisher;
    private final IncidentMetrics metrics;
    private final Clock clock;

    public IncidentService(IncidentRepository incidents, IncidentHistoryRepository history,
                           OutboxWriter publisher, IncidentMetrics metrics, Clock clock) {
        this.incidents = incidents;
        this.history = history;
        this.publisher = publisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Incident> findAll(IncidentStatus status) {
        return status == null
                ? incidents.findAllByOrderByCreatedAtDesc()
                : incidents.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public Incident getById(UUID id) {
        return incidents.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<IncidentHistoryEntry> getHistory(UUID id) {
        getById(id);
        return history.findByIncidentIdOrderByOccurredAt(id);
    }

    public Incident acknowledge(UUID id) {
        Instant now = Instant.now(clock);
        Incident incident = getById(id);
        incident.acknowledge(now);
        record(incident, HistoryAction.ACKNOWLEDGED, null, now);
        publisher.publish(EventTypes.INCIDENT_ACKNOWLEDGED, incident, null, UUID.randomUUID());
        return incident;
    }

    public Incident startInvestigation(UUID id) {
        Instant now = Instant.now(clock);
        Incident incident = getById(id);
        incident.startInvestigation(now);
        record(incident, HistoryAction.INVESTIGATION_STARTED, null, now);
        publisher.publish(EventTypes.INCIDENT_INVESTIGATION_STARTED, incident, null, UUID.randomUUID());
        return incident;
    }

    public Incident resolve(UUID id) {
        Instant now = Instant.now(clock);
        Incident incident = getById(id);
        incident.resolve(now);
        record(incident, HistoryAction.RESOLVED, null, now);
        publisher.publish(EventTypes.INCIDENT_RESOLVED, incident, null, UUID.randomUUID());
        metrics.recordResolved(incident, now);
        return incident;
    }

    public IncidentHistoryEntry addNote(UUID id, String note) {
        Instant now = Instant.now(clock);
        Incident incident = getById(id);
        IncidentHistoryEntry entry = record(incident, HistoryAction.NOTE_ADDED, note, now);
        publisher.publish(EventTypes.INCIDENT_NOTE_ADDED, incident, note, UUID.randomUUID());
        return entry;
    }

    private IncidentHistoryEntry record(Incident incident, HistoryAction action, String note, Instant occurredAt) {
        return history.save(IncidentHistoryEntry.of(incident.getId(), action, note, occurredAt));
    }
}
