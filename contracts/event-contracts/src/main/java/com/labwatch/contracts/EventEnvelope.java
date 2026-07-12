package com.labwatch.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Common envelope for all LabWatch domain events (see docs/event-catalog.md).
 * All metadata fields are required.
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        Instant producedAt,
        UUID correlationId,
        String producer,
        T payload
) {
}
