package com.labwatch.incident.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.Topics;
import com.labwatch.contracts.incident.IncidentEventPayload;
import com.labwatch.incident.domain.Incident;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes incident lifecycle events, keyed by deviceId for per-device ordering.
 *
 * Publishing is asynchronous and best-effort: a Kafka outage must not fail the
 * operator's REST request. The lost-event window is closed by the transactional
 * outbox in roadmap Phase 6.
 */
@Component
public class IncidentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(IncidentEventPublisher.class);
    private static final String PRODUCER_NAME = "incident-service";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IncidentEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                  Clock clock) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void publish(String eventType, Incident incident, String note, UUID correlationId) {
        Instant now = Instant.now(clock);
        IncidentEventPayload payload = new IncidentEventPayload(incident.getId(), incident.getDeviceId(),
                incident.getMetric(), incident.getSeverity(), incident.getStatus(), incident.getReason(), note);
        EventEnvelope<IncidentEventPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID(), eventType, 1, now, now, correlationId, PRODUCER_NAME, payload);
        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(Topics.INCIDENT_EVENTS_V1, incident.getDeviceId(), json)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            log.error("Failed to publish {} for incident {}", eventType, incident.getId(), exception);
                        }
                    });
        } catch (JsonProcessingException exception) {
            log.error("Failed to serialize {} for incident {}", eventType, incident.getId(), exception);
        }
    }
}
