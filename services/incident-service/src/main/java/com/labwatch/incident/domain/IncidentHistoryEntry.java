package com.labwatch.incident.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_history")
public class IncidentHistoryEntry {

    @Id
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HistoryAction action;

    private String note;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected IncidentHistoryEntry() {
    }

    private IncidentHistoryEntry(UUID id, UUID incidentId, HistoryAction action, String note, Instant occurredAt) {
        this.id = id;
        this.incidentId = incidentId;
        this.action = action;
        this.note = note;
        this.occurredAt = occurredAt;
    }

    public static IncidentHistoryEntry of(UUID incidentId, HistoryAction action, String note, Instant occurredAt) {
        return new IncidentHistoryEntry(UUID.randomUUID(), incidentId, action, note, occurredAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public HistoryAction getAction() {
        return action;
    }

    public String getNote() {
        return note;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
