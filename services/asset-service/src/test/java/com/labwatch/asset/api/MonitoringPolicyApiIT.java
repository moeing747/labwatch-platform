package com.labwatch.asset.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.labwatch.asset.PostgresContainerSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MonitoringPolicyApiIT extends PostgresContainerSupport {

    @Autowired
    private TestRestTemplate rest;

    private String deviceId;

    @BeforeEach
    void createDevice() {
        deviceId = "chamber-" + System.nanoTime();
        ResponseEntity<Map> device = rest.postForEntity("/api/devices",
                Map.of("deviceId", deviceId, "name", "Chamber"), Map.class);
        assertThat(device.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String policiesUrl() {
        return "/api/devices/" + deviceId + "/monitoring-policies";
    }

    @Test
    void should_create_and_list_policy() {
        ResponseEntity<Map> created = rest.postForEntity(policiesUrl(),
                Map.of("metric", "TEMPERATURE", "minimum", 2.0, "maximum", 8.0,
                        "violationDurationSeconds", 180, "severity", "HIGH"),
                Map.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("metric", "TEMPERATURE");
        assertThat(created.getBody()).containsEntry("deviceId", deviceId);

        ResponseEntity<List> list = rest.getForEntity(policiesUrl(), List.class);

        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).hasSize(1);
    }

    @Test
    void should_reject_policy_when_minimum_exceeds_maximum() {
        ResponseEntity<Map> response = rest.postForEntity(policiesUrl(),
                Map.of("metric", "TEMPERATURE", "minimum", 9.0, "maximum", 8.0,
                        "violationDurationSeconds", 180, "severity", "HIGH"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("title", "Validation failed");
        assertThat((String) response.getBody().get("detail")).contains("minimum");
    }

    @Test
    void should_reject_second_policy_for_same_metric() {
        Map<String, Object> policy = Map.of("metric", "HUMIDITY", "minimum", 30.0, "maximum", 60.0,
                "violationDurationSeconds", 300, "severity", "MEDIUM");
        rest.postForEntity(policiesUrl(), policy, Map.class);

        ResponseEntity<Map> duplicate = rest.postForEntity(policiesUrl(), policy, Map.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void should_return_not_found_for_policy_on_unknown_device() {
        ResponseEntity<Map> response = rest.postForEntity("/api/devices/ghost/monitoring-policies",
                Map.of("metric", "TEMPERATURE", "minimum", 2.0, "maximum", 8.0,
                        "violationDurationSeconds", 180, "severity", "HIGH"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_publish_policy_snapshot_event_on_create_and_delete() {
        try (var consumer = policyTopicConsumer("policy-events-it")) {
            ResponseEntity<Map> created = rest.postForEntity(policiesUrl(),
                    Map.of("metric", "TEMPERATURE", "minimum", 2.0, "maximum", 8.0,
                            "violationDurationSeconds", 180, "severity", "HIGH"),
                    Map.class);
            String policyId = (String) created.getBody().get("id");

            String snapshot = awaitSnapshotFor(consumer, value -> value.contains("\"TEMPERATURE\""));
            assertThat(snapshot).contains("\"DEVICE_MONITORING_POLICY_UPDATED\"");

            rest.exchange(policiesUrl() + "/" + policyId, HttpMethod.DELETE, null, Void.class);

            awaitSnapshotFor(consumer, value -> value.contains("\"policies\":[]"));
        }
    }

    @Test
    void should_publish_empty_snapshot_when_device_with_policies_is_deleted() {
        try (var consumer = policyTopicConsumer("device-delete-it")) {
            rest.postForEntity(policiesUrl(),
                    Map.of("metric", "TEMPERATURE", "minimum", 2.0, "maximum", 8.0,
                            "violationDurationSeconds", 180, "severity", "HIGH"),
                    Map.class);
            awaitSnapshotFor(consumer, value -> value.contains("\"TEMPERATURE\""));

            rest.exchange("/api/devices/" + deviceId, HttpMethod.DELETE, null, Void.class);

            awaitSnapshotFor(consumer, value -> value.contains("\"policies\":[]"));
        }
    }

    private org.apache.kafka.clients.consumer.Consumer<String, String> policyTopicConsumer(String group) {
        Map<String, Object> consumerProps = org.springframework.kafka.test.utils.KafkaTestUtils
                .consumerProps(kafkaBootstrapServers(), group + System.nanoTime(), "true");
        var consumer = new org.springframework.kafka.core.DefaultKafkaConsumerFactory<>(consumerProps,
                new org.apache.kafka.common.serialization.StringDeserializer(),
                new org.apache.kafka.common.serialization.StringDeserializer()).createConsumer();
        consumer.subscribe(List.of("device.policy-updated.v1"));
        return consumer;
    }

    /**
     * The topic is shared by every test in the JVM, so filter to this test's
     * device and the expected content instead of assuming a single record.
     */
    private String awaitSnapshotFor(org.apache.kafka.clients.consumer.Consumer<String, String> consumer,
                                    java.util.function.Predicate<String> expected) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            for (var record : consumer.poll(java.time.Duration.ofMillis(250))) {
                if (deviceId.equals(record.key()) && expected.test(record.value())) {
                    return record.value();
                }
            }
        }
        throw new AssertionError("No matching policy snapshot for device " + deviceId);
    }

    @Test
    void should_update_and_delete_policy() {
        ResponseEntity<Map> created = rest.postForEntity(policiesUrl(),
                Map.of("metric", "TEMPERATURE", "minimum", 2.0, "maximum", 8.0,
                        "violationDurationSeconds", 180, "severity", "HIGH"),
                Map.class);
        String policyId = (String) created.getBody().get("id");

        ResponseEntity<Map> updated = rest.exchange(policiesUrl() + "/" + policyId, HttpMethod.PUT,
                new HttpEntity<>(Map.of("minimum", 1.0, "maximum", 9.0,
                        "violationDurationSeconds", 240, "severity", "CRITICAL")),
                Map.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).containsEntry("severity", "CRITICAL");
        assertThat(updated.getBody()).containsEntry("violationDurationSeconds", 240);

        ResponseEntity<Void> deleted = rest.exchange(policiesUrl() + "/" + policyId,
                HttpMethod.DELETE, null, Void.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.getForEntity(policiesUrl(), List.class).getBody()).isEmpty();
    }
}
