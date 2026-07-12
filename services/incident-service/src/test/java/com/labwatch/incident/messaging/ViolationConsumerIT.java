package com.labwatch.incident.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.EventTypes;
import com.labwatch.contracts.Topics;
import com.labwatch.contracts.incident.IncidentStatus;
import com.labwatch.contracts.monitoring.ViolationResolvedPayload;
import com.labwatch.contracts.monitoring.ViolationStartedPayload;
import com.labwatch.contracts.policy.Metric;
import com.labwatch.contracts.policy.Severity;
import com.labwatch.incident.ContainerSupport;
import com.labwatch.incident.domain.HistoryAction;
import com.labwatch.incident.domain.Incident;
import com.labwatch.incident.domain.IncidentHistoryRepository;
import com.labwatch.incident.domain.IncidentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ViolationConsumerIT extends ContainerSupport {

    private static final Instant T0 = Instant.parse("2026-07-12T12:00:00Z");
    private static final String DEVICE = "it-chamber-100";

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private IncidentHistoryRepository history;

    @Autowired
    private MeterRegistry meterRegistry;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void should_create_exactly_one_incident_when_violation_event_is_delivered_twice() throws Exception {
        UUID violationEventId = UUID.randomUUID();
        String started = startedJson(violationEventId);

        try (KafkaProducer<String, String> producer = producer()) {
            producer.send(new ProducerRecord<>(Topics.MONITORING_VIOLATIONS_V1, DEVICE, started)).get();
            producer.send(new ProducerRecord<>(Topics.MONITORING_VIOLATIONS_V1, DEVICE, started)).get();
            producer.send(new ProducerRecord<>(Topics.MONITORING_VIOLATIONS_V1, DEVICE, resolvedJson())).get();
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<Incident> stored = incidents.findAllByOrderByCreatedAtDesc().stream()
                    .filter(incident -> incident.getDeviceId().equals(DEVICE)).toList();
            assertThat(stored).hasSize(1);
            assertThat(history.findByIncidentIdOrderByOccurredAt(stored.getFirst().getId()))
                    .extracting(entry -> entry.getAction())
                    .containsExactly(HistoryAction.OPENED, HistoryAction.VIOLATION_RECOVERED);
        });

        Incident incident = incidents.findAllByOrderByCreatedAtDesc().stream()
                .filter(i -> i.getDeviceId().equals(DEVICE)).findFirst().orElseThrow();
        assertThat(incident.getTriggeringEventId()).isEqualTo(violationEventId);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getReason()).isEqualTo("TEMPERATURE_ABOVE_LIMIT");
        assertThat(incident.getMeasuredValue()).isEqualByComparingTo("9.40");
        // The opened counter is asserted in IncidentApiIT: with cached test
        // contexts sharing one consumer group, this record may be consumed -
        // and counted - by another context's listener. The gauge reads the
        // shared database, so it is deterministic here.
        assertThat(meterRegistry.get("labwatch.incidents.open.current").gauge().value())
                .isGreaterThanOrEqualTo(1.0);
    }

    private String startedJson(UUID eventId) throws Exception {
        EventEnvelope<ViolationStartedPayload> envelope = new EventEnvelope<>(
                eventId, EventTypes.violationStarted("TEMPERATURE"), 1, T0, T0,
                UUID.randomUUID(), "monitoring-service",
                new ViolationStartedPayload(DEVICE, Metric.TEMPERATURE, new BigDecimal("9.40"),
                        new BigDecimal("8.00"), T0.minusSeconds(180), T0, Severity.HIGH));
        return mapper.writeValueAsString(envelope);
    }

    private String resolvedJson() throws Exception {
        EventEnvelope<ViolationResolvedPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID(), EventTypes.violationResolved("TEMPERATURE"), 1,
                T0.plusSeconds(300), T0.plusSeconds(300), UUID.randomUUID(), "monitoring-service",
                new ViolationResolvedPayload(DEVICE, Metric.TEMPERATURE, new BigDecimal("7.20"),
                        T0.plusSeconds(300)));
        return mapper.writeValueAsString(envelope);
    }

    private static KafkaProducer<String, String> producer() {
        return new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }
}
