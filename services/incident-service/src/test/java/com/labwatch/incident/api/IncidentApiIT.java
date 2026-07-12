package com.labwatch.incident.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.EventTypes;
import com.labwatch.contracts.monitoring.ViolationStartedPayload;
import com.labwatch.contracts.policy.Metric;
import com.labwatch.contracts.policy.Severity;
import com.labwatch.incident.ContainerSupport;
import com.labwatch.incident.application.ViolationEventHandler;
import com.labwatch.incident.domain.IncidentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IncidentApiIT extends ContainerSupport {

    private static final Instant T0 = Instant.parse("2026-07-12T12:00:00Z");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ViolationEventHandler handler;

    @Autowired
    private IncidentRepository incidents;

    private String openIncident() {
        String device = "api-chamber-" + System.nanoTime();
        handler.onViolationStarted(new EventEnvelope<>(
                UUID.randomUUID(), EventTypes.violationStarted("TEMPERATURE"), 1, T0, T0,
                UUID.randomUUID(), "monitoring-service",
                new ViolationStartedPayload(device, Metric.TEMPERATURE, new BigDecimal("9.40"),
                        new BigDecimal("8.00"), T0.minusSeconds(180), T0, Severity.HIGH)));
        return incidents.findAllByOrderByCreatedAtDesc().stream()
                .filter(incident -> incident.getDeviceId().equals(device))
                .findFirst().orElseThrow().getId().toString();
    }

    @Test
    void should_move_incident_through_full_lifecycle_and_record_history() {
        String id = openIncident();

        assertThat(post(id, "acknowledge").getBody()).containsEntry("status", "ACKNOWLEDGED");
        assertThat(post(id, "investigate").getBody()).containsEntry("status", "INVESTIGATING");

        ResponseEntity<Map> resolved = post(id, "resolve");
        assertThat(resolved.getBody()).containsEntry("status", "RESOLVED");
        assertThat(resolved.getBody().get("resolvedAt")).isNotNull();

        ResponseEntity<List> history = rest.getForEntity("/api/incidents/" + id + "/history", List.class);
        assertThat(history.getBody()).extracting(entry -> ((Map<String, ?>) entry).get("action"))
                .containsExactly("OPENED", "ACKNOWLEDGED", "INVESTIGATION_STARTED", "RESOLVED");
    }

    @Test
    void should_reject_invalid_transition_with_conflict_problem() {
        String id = openIncident();

        ResponseEntity<Map> response = post(id, "resolve");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("title", "Invalid state transition");
    }

    @Test
    void should_add_note_to_incident() {
        String id = openIncident();

        ResponseEntity<Map> note = rest.postForEntity("/api/incidents/" + id + "/notes",
                Map.of("text", "Compressor inspected, fan blocked"), Map.class);

        assertThat(note.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(note.getBody()).containsEntry("action", "NOTE_ADDED");
        assertThat(note.getBody()).containsEntry("note", "Compressor inspected, fan blocked");
    }

    @Test
    void should_reject_blank_note() {
        String id = openIncident();

        ResponseEntity<Map> response = rest.postForEntity("/api/incidents/" + id + "/notes",
                Map.of("text", " "), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_return_not_found_for_unknown_incident() {
        ResponseEntity<Map> response = rest.getForEntity("/api/incidents/" + UUID.randomUUID(), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("title", "Resource not found");
    }

    @Test
    void should_filter_incidents_by_status() {
        String id = openIncident();
        post(id, "acknowledge");

        ResponseEntity<List> acknowledged = rest.getForEntity("/api/incidents?status=ACKNOWLEDGED", List.class);

        assertThat(acknowledged.getBody()).extracting(entry -> ((Map<String, ?>) entry).get("id"))
                .contains(id);
    }

    private ResponseEntity<Map> post(String id, String action) {
        return rest.postForEntity("/api/incidents/" + id + "/" + action, null, Map.class);
    }
}
