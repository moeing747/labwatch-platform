package com.labwatch.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.EventTypes;
import com.labwatch.contracts.Topics;
import com.labwatch.contracts.policy.DevicePoliciesPayload;
import com.labwatch.contracts.policy.Metric;
import com.labwatch.contracts.policy.MonitoringPolicySnapshot;
import com.labwatch.contracts.policy.Severity;
import com.labwatch.contracts.telemetry.DeviceOperatingState;
import com.labwatch.contracts.telemetry.DeviceTelemetryPayload;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest
class MonitoringEndToEndIT {

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.1");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) throws Exception {
        String stateDir = java.nio.file.Files.createTempDirectory("monitoring-it-state").toString();
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.streams.properties.state.dir", () -> stateDir);
    }

    private static final Instant T0 = Instant.parse("2026-07-12T12:00:00Z");
    private static final String DEVICE = "e2e-chamber-001";

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void should_detect_sustained_violation_and_recovery_end_to_end() throws Exception {
        try (KafkaProducer<String, String> producer = producer()) {
            producer.send(new ProducerRecord<>(Topics.DEVICE_POLICY_UPDATED_V1, DEVICE, policyJson())).get();
            producer.send(new ProducerRecord<>(Topics.DEVICE_TELEMETRY_V1, DEVICE, telemetryJson("9.0", T0))).get();
            producer.send(new ProducerRecord<>(Topics.DEVICE_TELEMETRY_V1, DEVICE, telemetryJson("9.4", T0.plusSeconds(180)))).get();
            producer.send(new ProducerRecord<>(Topics.DEVICE_TELEMETRY_V1, DEVICE, telemetryJson("7.2", T0.plusSeconds(300)))).get();
        }

        List<JsonNode> events = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(Topics.MONITORING_VIOLATIONS_V1));
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500))
                        .forEach(record -> events.add(readTree(record.value())));
                assertThat(events).hasSize(2);
            });
        }

        assertThat(events.get(0).get("eventType").asText()).isEqualTo("TEMPERATURE_VIOLATION_STARTED");
        assertThat(events.get(0).at("/payload/violationStartedAt").asText()).isEqualTo(T0.toString());
        assertThat(events.get(1).get("eventType").asText()).isEqualTo("TEMPERATURE_VIOLATION_RESOLVED");
        assertThat(events.get(1).at("/payload/recoveredValue").decimalValue()).isEqualByComparingTo("7.2");
    }

    private String policyJson() throws Exception {
        EventEnvelope<DevicePoliciesPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID(), EventTypes.DEVICE_MONITORING_POLICY_UPDATED, 1, T0, T0,
                UUID.randomUUID(), "asset-service",
                new DevicePoliciesPayload(DEVICE, List.of(new MonitoringPolicySnapshot(
                        Metric.TEMPERATURE, new BigDecimal("2.0"), new BigDecimal("8.0"), 180, Severity.HIGH))));
        return mapper.writeValueAsString(envelope);
    }

    private String telemetryJson(String temperature, Instant occurredAt) throws Exception {
        EventEnvelope<DeviceTelemetryPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID(), EventTypes.DEVICE_TELEMETRY_RECEIVED, 1, occurredAt, occurredAt,
                UUID.randomUUID(), "telemetry-service",
                new DeviceTelemetryPayload(DEVICE, new BigDecimal(temperature), new BigDecimal("50.0"),
                        DeviceOperatingState.RUNNING));
        return mapper.writeValueAsString(envelope);
    }

    private JsonNode readTree(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static KafkaProducer<String, String> producer() {
        return new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }

    private static KafkaConsumer<String, String> consumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "monitoring-e2e-it",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }
}
