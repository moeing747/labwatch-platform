package com.labwatch.incident.api;

import com.labwatch.contracts.incident.IncidentStatus;
import com.labwatch.contracts.policy.Metric;
import com.labwatch.contracts.policy.Severity;
import com.labwatch.incident.domain.HistoryAction;
import com.labwatch.incident.domain.Incident;
import com.labwatch.incident.domain.IncidentHistoryEntry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class IncidentDtos {

    private IncidentDtos() {
    }

    public record IncidentResponse(
            UUID id,
            String deviceId,
            Metric metric,
            Severity severity,
            IncidentStatus status,
            String reason,
            BigDecimal measuredValue,
            BigDecimal threshold,
            Instant violationStartedAt,
            Instant detectedAt,
            Instant resolvedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static IncidentResponse from(Incident incident) {
            return new IncidentResponse(incident.getId(), incident.getDeviceId(), incident.getMetric(),
                    incident.getSeverity(), incident.getStatus(), incident.getReason(),
                    incident.getMeasuredValue(), incident.getThreshold(), incident.getViolationStartedAt(),
                    incident.getDetectedAt(), incident.getResolvedAt(), incident.getCreatedAt(),
                    incident.getUpdatedAt());
        }
    }

    public record HistoryResponse(
            HistoryAction action,
            String note,
            Instant occurredAt
    ) {
        public static HistoryResponse from(IncidentHistoryEntry entry) {
            return new HistoryResponse(entry.getAction(), entry.getNote(), entry.getOccurredAt());
        }
    }

    public record NoteRequest(
            @NotBlank @Size(max = 1000)
            String text
    ) {
    }
}
