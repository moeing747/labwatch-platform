package com.labwatch.telemetry.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.Topics;
import com.labwatch.contracts.telemetry.DeviceOperatingState;
import com.labwatch.contracts.telemetry.DeviceTelemetryPayload;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

// @SpringBootTest disables metrics export by default; observability must be
// re-enabled for the /actuator/prometheus endpoint to exist in this test.
@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TelemetryApiIT {

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.1");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    private static Consumer<String, String> consumer;

    @BeforeAll
    static void startConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                KAFKA.getBootstrapServers(), "telemetry-api-it", "true");
        consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer();
        consumer.subscribe(java.util.List.of(Topics.DEVICE_TELEMETRY_V1));
    }

    @AfterAll
    static void stopConsumer() {
        consumer.close();
    }

    @Test
    void should_publish_valid_telemetry_to_kafka() throws Exception {
        Instant timestamp = Instant.now().minusSeconds(5);
        ResponseEntity<Map> response = rest.postForEntity("/api/telemetry",
                Map.of("deviceId", "chamber-042",
                        "timestamp", timestamp.toString(),
                        "temperature", 9.4,
                        "humidity", 61.2,
                        "operatingState", "RUNNING"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsKeys("eventId", "correlationId");

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(consumer, Topics.DEVICE_TELEMETRY_V1, Duration.ofSeconds(10));

        assertThat(record.key()).isEqualTo("chamber-042");
        EventEnvelope<DeviceTelemetryPayload> envelope = objectMapper.readValue(record.value(),
                new TypeReference<>() {
                });
        assertThat(envelope.eventType()).isEqualTo("DEVICE_TELEMETRY_RECEIVED");
        assertThat(envelope.eventId().toString()).isEqualTo(response.getBody().get("eventId"));
        assertThat(envelope.occurredAt()).isEqualTo(timestamp);
        assertThat(envelope.payload().deviceId()).isEqualTo("chamber-042");
        assertThat(envelope.payload().temperature()).isEqualByComparingTo("9.40");
        assertThat(envelope.payload().operatingState()).isEqualTo(DeviceOperatingState.RUNNING);
    }

    @Test
    void should_reject_invalid_telemetry_without_publishing() {
        ResponseEntity<Map> missingDevice = rest.postForEntity("/api/telemetry",
                Map.of("timestamp", Instant.now().toString(),
                        "temperature", 5.0, "humidity", 150.0, "operatingState", "RUNNING"),
                Map.class);

        assertThat(missingDevice.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((Map<String, ?>) missingDevice.getBody().get("fieldErrors"))
                .containsKeys("deviceId", "humidity");
    }

    @Test
    void should_expose_telemetry_metrics_when_prometheus_endpoint_is_scraped() {
        // Only rejected telemetry: publishing here would leak an extra record
        // into the topic the publish test reads with getSingleRecord. The
        // received counter and publish timer are registered eagerly, so they
        // appear in the scrape without traffic.
        rest.postForEntity("/api/telemetry",
                Map.of("deviceId", "chamber-042",
                        "timestamp", Instant.now().plusSeconds(3600).toString(),
                        "temperature", 5.0, "humidity", 50.0, "operatingState", "RUNNING"),
                Map.class);

        ResponseEntity<String> scrape = rest.getForEntity("/actuator/prometheus", String.class);

        assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scrape.getBody())
                .contains("labwatch_telemetry_received_total")
                .contains("labwatch_telemetry_rejected_total")
                .contains("reason=\"invalid_timestamp\"")
                .contains("labwatch_telemetry_publish_duration_seconds_count");
    }

    @Test
    void should_reject_telemetry_with_future_timestamp() {
        ResponseEntity<Map> response = rest.postForEntity("/api/telemetry",
                Map.of("deviceId", "chamber-042",
                        "timestamp", Instant.now().plusSeconds(3600).toString(),
                        "temperature", 5.0, "humidity", 50.0, "operatingState", "RUNNING"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("detail")).contains("future");
    }
}
