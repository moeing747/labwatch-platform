package com.labwatch.incident.application;

import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.EventTypes;
import com.labwatch.contracts.incident.IncidentStatus;
import com.labwatch.contracts.monitoring.ViolationResolvedPayload;
import com.labwatch.contracts.monitoring.ViolationStartedPayload;
import com.labwatch.incident.domain.HistoryAction;
import com.labwatch.incident.domain.Incident;
import com.labwatch.incident.domain.IncidentHistoryEntry;
import com.labwatch.incident.domain.IncidentHistoryRepository;
import com.labwatch.incident.domain.IncidentRepository;
import com.labwatch.incident.domain.ProcessedEvent;
import com.labwatch.incident.domain.ProcessedEventRepository;
import com.labwatch.incident.messaging.IncidentEventPublisher;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes monitoring violations. Idempotent: every event's ID is recorded in
 * processed_events within the same transaction as its effect, so a redelivered
 * event is recognized and skipped. The unique constraint on the incident's
 * triggering_event_id is the second line of defense.
 */
@Service
@Transactional
public class ViolationEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ViolationEventHandler.class);

    private final IncidentRepository incidents;
    private final IncidentHistoryRepository history;
    private final ProcessedEventRepository processedEvents;
    private final IncidentEventPublisher publisher;
    private final Clock clock;

    public ViolationEventHandler(IncidentRepository incidents, IncidentHistoryRepository history,
                                 ProcessedEventRepository processedEvents, IncidentEventPublisher publisher,
                                 Clock clock) {
        this.incidents = incidents;
        this.history = history;
        this.processedEvents = processedEvents;
        this.publisher = publisher;
        this.clock = clock;
    }

    public void onViolationStarted(EventEnvelope<ViolationStartedPayload> envelope) {
        if (alreadyProcessed(envelope)) {
            return;
        }
        Instant now = Instant.now(clock);
        Incident incident = incidents.save(Incident.open(envelope.eventId(), envelope.payload(), now));
        history.save(IncidentHistoryEntry.of(incident.getId(), HistoryAction.OPENED,
                "Opened from %s (measured %s, threshold %s)".formatted(
                        envelope.eventType(), envelope.payload().measuredValue(), envelope.payload().threshold()),
                now));
        markProcessed(envelope, now);
        publisher.publish(EventTypes.INCIDENT_OPENED, incident, null, envelope.correlationId());
        log.info("Incident {} opened for device {} ({})", incident.getId(), incident.getDeviceId(),
                incident.getReason());
    }

    public void onViolationResolved(EventEnvelope<ViolationResolvedPayload> envelope) {
        if (alreadyProcessed(envelope)) {
            return;
        }
        Instant now = Instant.now(clock);
        ViolationResolvedPayload payload = envelope.payload();
        incidents.findFirstByDeviceIdAndMetricAndStatusNotOrderByCreatedAtDesc(
                        payload.deviceId(), payload.metric(), IncidentStatus.RESOLVED)
                .ifPresentOrElse(incident -> history.save(IncidentHistoryEntry.of(
                                incident.getId(), HistoryAction.VIOLATION_RECOVERED,
                                "Measured value recovered to " + payload.recoveredValue(), now)),
                        () -> log.info("Violation recovered for device {} but no open incident found",
                                payload.deviceId()));
        markProcessed(envelope, now);
    }

    private boolean alreadyProcessed(EventEnvelope<?> envelope) {
        boolean processed = processedEvents.existsById(envelope.eventId());
        if (processed) {
            log.info("Skipping duplicate event {} ({})", envelope.eventId(), envelope.eventType());
        }
        return processed;
    }

    private void markProcessed(EventEnvelope<?> envelope, Instant now) {
        processedEvents.save(new ProcessedEvent(envelope.eventId(), now));
    }
}
