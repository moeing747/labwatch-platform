package com.labwatch.incident.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.EventTypes;
import com.labwatch.contracts.Topics;
import com.labwatch.contracts.monitoring.ViolationStartedPayload;
import com.labwatch.contracts.policy.Metric;
import com.labwatch.contracts.policy.Severity;
import com.labwatch.incident.ContainerSupport;
import com.labwatch.incident.application.ViolationEventHandler;
import com.labwatch.incident.domain.IncidentRepository;
import com.labwatch.incident.domain.OutboxEvent;
import com.labwatch.incident.domain.OutboxEventRepository;
import com.labwatch.incident.domain.ProcessedEventRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class OutboxIT extends ContainerSupport {

    private static final Instant T0 = Instant.parse("2026-07-12T12:00:00Z");

    @Autowired
    private ViolationEventHandler handler;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private OutboxEventRepository outbox;

    @Autowired
    private ProcessedEventRepository processedEvents;

    @Test
    void should_roll_back_incident_and_outbox_together_when_transaction_fails() {
        String device = "outbox-rollback-" + System.nanoTime();
        UUID violationEventId = UUID.randomUUID();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(tx -> {
            handler.onViolationStarted(startedEnvelope(device, violationEventId));
            throw new RuntimeException("simulated crash after business logic");
        })).hasMessageContaining("simulated crash");

        assertThat(incidents.findAllByOrderByCreatedAtDesc())
                .noneMatch(incident -> incident.getDeviceId().equals(device));
        assertThat(outbox.findAll())
                .noneMatch(event -> event.getMessageKey().equals(device));
        assertThat(processedEvents.existsById(violationEventId)).isFalse();
    }

    @Test
    void should_publish_pending_outbox_event_and_mark_it_published() {
        String device = "outbox-publish-" + System.nanoTime();
        handler.onViolationStarted(startedEnvelope(device, UUID.randomUUID()));

        OutboxEvent written = outbox.findAll().stream()
                .filter(event -> event.getMessageKey().equals(device))
                .findFirst().orElseThrow();
        assertThat(written.getEventType()).isEqualTo(EventTypes.INCIDENT_OPENED);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            OutboxEvent current = outbox.findById(written.getId()).orElseThrow();
            assertThat(current.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
            assertThat(current.getPublishedAt()).isNotNull();
        });

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-it-" + device,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(Topics.INCIDENT_EVENTS_V1));
            boolean[] found = {false};
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (device.equals(record.key())
                            && record.value().contains(written.getEventId().toString())) {
                        found[0] = true;
                    }
                }
                assertThat(found[0]).as("outbox event should reach incident.events.v1").isTrue();
            });
        }
    }

    private EventEnvelope<ViolationStartedPayload> startedEnvelope(String device, UUID eventId) {
        return new EventEnvelope<>(eventId, EventTypes.violationStarted("TEMPERATURE"), 1, T0, T0,
                UUID.randomUUID(), "monitoring-service",
                new ViolationStartedPayload(device, Metric.TEMPERATURE, new BigDecimal("9.40"),
                        new BigDecimal("8.00"), T0.minusSeconds(180), T0, Severity.HIGH));
    }
}
