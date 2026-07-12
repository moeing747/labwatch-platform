package com.labwatch.incident.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.Topics;
import com.labwatch.contracts.incident.IncidentEventPayload;
import com.labwatch.incident.domain.Incident;
import com.labwatch.incident.domain.OutboxEvent;
import com.labwatch.incident.domain.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Writes incident lifecycle events into the outbox table. Callers invoke this
 * inside their @Transactional method, so the state change and its event commit
 * or roll back together. The OutboxPublisher delivers them to Kafka afterwards.
 */
@Component
public class OutboxWriter {

    private static final String PRODUCER_NAME = "incident-service";
    private static final String AGGREGATE_TYPE = "INCIDENT";

    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxWriter(OutboxEventRepository outbox, ObjectMapper objectMapper, Clock clock) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void publish(String eventType, Incident incident, String note, UUID correlationId) {
        Instant now = Instant.now(clock);
        UUID eventId = UUID.randomUUID();
        IncidentEventPayload payload = new IncidentEventPayload(incident.getId(), incident.getDeviceId(),
                incident.getMetric(), incident.getSeverity(), incident.getStatus(), incident.getReason(), note);
        EventEnvelope<IncidentEventPayload> envelope = new EventEnvelope<>(
                eventId, eventType, 1, now, now, correlationId, PRODUCER_NAME, payload);
        try {
            outbox.save(OutboxEvent.pending(eventId, AGGREGATE_TYPE, incident.getId().toString(), eventType,
                    Topics.INCIDENT_EVENTS_V1, incident.getDeviceId(),
                    objectMapper.writeValueAsString(envelope), now));
        } catch (JsonProcessingException exception) {
            // Serialization of our own contract records failing is a programming
            // error; failing the transaction is the correct outcome.
            throw new IllegalStateException("Failed to serialize " + eventType, exception);
        }
    }
}
