package com.labwatch.telemetrystorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.EventTypes;
import com.labwatch.contracts.Topics;
import com.labwatch.contracts.telemetry.DeviceOperatingState;
import com.labwatch.contracts.telemetry.DeviceTelemetryPayload;
import com.labwatch.telemetrystorage.domain.TelemetryReading;
import com.labwatch.telemetrystorage.domain.TelemetryReadingRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
class TelemetryPersistenceIT {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.1");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private TelemetryReadingRepository readings;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void should_persist_each_event_exactly_once_when_duplicates_arrive() throws Exception {
        UUID firstEventId = UUID.randomUUID();
        String first = telemetryJson(firstEventId, "it-chamber-001", "9.40");
        String second = telemetryJson(UUID.randomUUID(), "it-chamber-001", "9.60");

        try (KafkaProducer<String, String> producer = producer()) {
            producer.send(new ProducerRecord<>(Topics.DEVICE_TELEMETRY_V1, "it-chamber-001", first)).get();
            producer.send(new ProducerRecord<>(Topics.DEVICE_TELEMETRY_V1, "it-chamber-001", first)).get();
            producer.send(new ProducerRecord<>(Topics.DEVICE_TELEMETRY_V1, "it-chamber-001", second)).get();
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<TelemetryReading> stored = readings.findByDeviceIdOrderByOccurredAt("it-chamber-001");
            assertThat(stored).hasSize(2);
        });

        List<TelemetryReading> stored = readings.findByDeviceIdOrderByOccurredAt("it-chamber-001");
        assertThat(stored).filteredOn(reading -> reading.getEventId().equals(firstEventId)).hasSize(1);
        assertThat(stored.getFirst().getTemperature()).isEqualByComparingTo("9.40");
        assertThat(stored.getFirst().getOperatingState()).isEqualTo(DeviceOperatingState.RUNNING);
    }

    private String telemetryJson(UUID eventId, String deviceId, String temperature) throws Exception {
        EventEnvelope<DeviceTelemetryPayload> envelope = new EventEnvelope<>(
                eventId, EventTypes.DEVICE_TELEMETRY_RECEIVED, 1,
                Instant.parse("2026-07-12T12:10:30Z"), Instant.parse("2026-07-12T12:10:31Z"),
                UUID.randomUUID(), "telemetry-service",
                new DeviceTelemetryPayload(deviceId, new BigDecimal(temperature),
                        new BigDecimal("61.20"), DeviceOperatingState.RUNNING));
        return objectMapper.writeValueAsString(envelope);
    }

    private static KafkaProducer<String, String> producer() {
        return new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }
}
