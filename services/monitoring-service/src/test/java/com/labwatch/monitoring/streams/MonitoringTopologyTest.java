package com.labwatch.monitoring.streams;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MonitoringTopologyTest {

    private static final Instant T0 = Instant.parse("2026-07-12T12:00:00Z");
    private static final String DEVICE = "chamber-042";

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @TempDir
    Path stateDir;

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> policyTopic;
    private TestInputTopic<String, String> telemetryTopic;
    private TestOutputTopic<String, String> violationsTopic;

    @BeforeEach
    void setUpTopology() {
        StreamsBuilder builder = new StreamsBuilder();
        new MonitoringTopology(mapper, Clock.fixed(T0, ZoneOffset.UTC)).violationStream(builder);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "monitoring-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.STATE_DIR_CONFIG, stateDir.toString());
        driver = new TopologyTestDriver(builder.build(), props);

        policyTopic = driver.createInputTopic(Topics.DEVICE_POLICY_UPDATED_V1,
                new StringSerializer(), new StringSerializer());
        telemetryTopic = driver.createInputTopic(Topics.DEVICE_TELEMETRY_V1,
                new StringSerializer(), new StringSerializer());
        violationsTopic = driver.createOutputTopic(Topics.MONITORING_VIOLATIONS_V1,
                new StringDeserializer(), new StringDeserializer());
    }

    @AfterEach
    void closeDriver() {
        driver.close();
    }

    @Test
    void should_not_emit_violation_when_spike_is_shorter_than_configured_duration() {
        givenTemperaturePolicy(180);

        pipeTemperature("9.0", T0);
        pipeTemperature("9.1", T0.plusSeconds(60));
        pipeTemperature("7.0", T0.plusSeconds(120));

        assertThat(violationsTopic.readValuesToList()).isEmpty();
    }

    @Test
    void should_emit_exactly_one_started_event_when_violation_is_sustained() {
        givenTemperaturePolicy(180);

        pipeTemperature("9.0", T0);
        pipeTemperature("9.2", T0.plusSeconds(60));
        pipeTemperature("9.4", T0.plusSeconds(180));
        pipeTemperature("9.5", T0.plusSeconds(240));

        List<JsonNode> events = readViolations();
        assertThat(events).hasSize(1);
        JsonNode started = events.getFirst();
        assertThat(started.get("eventType").asText()).isEqualTo("TEMPERATURE_VIOLATION_STARTED");
        assertThat(started.at("/payload/deviceId").asText()).isEqualTo(DEVICE);
        assertThat(started.at("/payload/measuredValue").decimalValue()).isEqualByComparingTo("9.4");
        assertThat(started.at("/payload/threshold").decimalValue()).isEqualByComparingTo("8.0");
        assertThat(started.at("/payload/violationStartedAt").asText()).isEqualTo(T0.toString());
        assertThat(started.at("/payload/detectedAt").asText()).isEqualTo(T0.plusSeconds(180).toString());
        assertThat(started.at("/payload/severity").asText()).isEqualTo("HIGH");
    }

    @Test
    void should_emit_resolved_event_when_notified_violation_recovers() {
        givenTemperaturePolicy(180);

        pipeTemperature("9.0", T0);
        pipeTemperature("9.4", T0.plusSeconds(180));
        pipeTemperature("7.5", T0.plusSeconds(300));

        List<JsonNode> events = readViolations();
        assertThat(events).hasSize(2);
        JsonNode resolved = events.get(1);
        assertThat(resolved.get("eventType").asText()).isEqualTo("TEMPERATURE_VIOLATION_RESOLVED");
        assertThat(resolved.at("/payload/recoveredValue").decimalValue()).isEqualByComparingTo("7.5");
        assertThat(resolved.at("/payload/resolvedAt").asText()).isEqualTo(T0.plusSeconds(300).toString());
    }

    @Test
    void should_start_new_violation_cycle_after_recovery() {
        givenTemperaturePolicy(180);

        pipeTemperature("9.0", T0);
        pipeTemperature("9.4", T0.plusSeconds(180));
        pipeTemperature("7.5", T0.plusSeconds(240));
        pipeTemperature("9.0", T0.plusSeconds(300));
        pipeTemperature("9.1", T0.plusSeconds(480));

        List<JsonNode> events = readViolations();
        assertThat(events).extracting(node -> node.get("eventType").asText()).containsExactly(
                "TEMPERATURE_VIOLATION_STARTED",
                "TEMPERATURE_VIOLATION_RESOLVED",
                "TEMPERATURE_VIOLATION_STARTED");
    }

    @Test
    void should_use_minimum_as_threshold_when_value_is_too_low() {
        givenTemperaturePolicy(180);

        pipeTemperature("1.0", T0);
        pipeTemperature("0.5", T0.plusSeconds(180));

        List<JsonNode> events = readViolations();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().at("/payload/threshold").decimalValue()).isEqualByComparingTo("2.0");
    }

    @Test
    void should_not_emit_anything_when_device_has_no_policy() {
        pipeTemperature("15.0", T0);
        pipeTemperature("15.0", T0.plusSeconds(600));

        assertThat(violationsTopic.readValuesToList()).isEmpty();
    }

    @Test
    void should_track_humidity_policy_independently() {
        givenPolicies(List.of(
                new MonitoringPolicySnapshot(Metric.TEMPERATURE, new BigDecimal("2.0"), new BigDecimal("8.0"), 180, Severity.HIGH),
                new MonitoringPolicySnapshot(Metric.HUMIDITY, new BigDecimal("30.0"), new BigDecimal("60.0"), 60, Severity.MEDIUM)));

        pipe(telemetry("5.0", "75.0", T0));
        pipe(telemetry("5.0", "76.0", T0.plusSeconds(60)));

        List<JsonNode> events = readViolations();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().get("eventType").asText()).isEqualTo("HUMIDITY_VIOLATION_STARTED");
        assertThat(events.getFirst().at("/payload/severity").asText()).isEqualTo("MEDIUM");
    }

    private void givenTemperaturePolicy(int durationSeconds) {
        givenPolicies(List.of(new MonitoringPolicySnapshot(
                Metric.TEMPERATURE, new BigDecimal("2.0"), new BigDecimal("8.0"), durationSeconds, Severity.HIGH)));
    }

    private void givenPolicies(List<MonitoringPolicySnapshot> policies) {
        EventEnvelope<DevicePoliciesPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID(), EventTypes.DEVICE_MONITORING_POLICY_UPDATED, 1, T0, T0,
                UUID.randomUUID(), "asset-service", new DevicePoliciesPayload(DEVICE, policies));
        policyTopic.pipeInput(DEVICE, toJson(envelope));
    }

    private void pipeTemperature(String temperature, Instant occurredAt) {
        pipe(telemetry(temperature, "50.0", occurredAt));
    }

    private EventEnvelope<DeviceTelemetryPayload> telemetry(String temperature, String humidity, Instant occurredAt) {
        return new EventEnvelope<>(UUID.randomUUID(), EventTypes.DEVICE_TELEMETRY_RECEIVED, 1,
                occurredAt, occurredAt, UUID.randomUUID(), "telemetry-service",
                new DeviceTelemetryPayload(DEVICE, new BigDecimal(temperature), new BigDecimal(humidity),
                        DeviceOperatingState.RUNNING));
    }

    private void pipe(EventEnvelope<DeviceTelemetryPayload> envelope) {
        telemetryTopic.pipeInput(DEVICE, toJson(envelope));
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private List<JsonNode> readViolations() {
        return violationsTopic.readValuesToList().stream().map(json -> {
            try {
                return mapper.readTree(json);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }).toList();
    }
}
