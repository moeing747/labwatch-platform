package com.labwatch.contracts.incident;

import com.labwatch.contracts.policy.Metric;
import com.labwatch.contracts.policy.Severity;
import java.util.UUID;

/**
 * Payload of INCIDENT_* lifecycle events on incident.events.v1.
 * {@code note} is present only for INCIDENT_NOTE_ADDED events.
 */
public record IncidentEventPayload(
        UUID incidentId,
        String deviceId,
        Metric metric,
        Severity severity,
        IncidentStatus status,
        String reason,
        String note
) {
}
