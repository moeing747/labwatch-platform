package com.labwatch.asset.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.labwatch.asset.PostgresContainerSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeviceApiIT extends PostgresContainerSupport {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void should_create_device_and_read_it_back() {
        ResponseEntity<Map> created = rest.postForEntity("/api/devices",
                Map.of("deviceId", "chamber-042", "name", "Cold chamber 42"), Map.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).hasPath("/api/devices/chamber-042");
        assertThat(created.getBody()).containsEntry("deviceId", "chamber-042");

        ResponseEntity<Map> fetched = rest.getForEntity("/api/devices/chamber-042", Map.class);

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).containsEntry("name", "Cold chamber 42");
    }

    @Test
    void should_reject_malformed_device_with_field_errors() {
        ResponseEntity<Map> response = rest.postForEntity("/api/devices",
                Map.of("deviceId", "UPPERCASE_INVALID", "name", ""), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).containsEntry("title", "Validation failed");
        assertThat((Map<String, ?>) response.getBody().get("fieldErrors"))
                .containsKeys("deviceId", "name");
    }

    @Test
    void should_return_problem_for_unknown_device() {
        ResponseEntity<Map> response = rest.getForEntity("/api/devices/does-not-exist", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).containsEntry("title", "Resource not found");
    }

    @Test
    void should_reject_duplicate_device_id() {
        rest.postForEntity("/api/devices",
                Map.of("deviceId", "chamber-dup", "name", "First"), Map.class);

        ResponseEntity<Map> duplicate = rest.postForEntity("/api/devices",
                Map.of("deviceId", "chamber-dup", "name", "Second"), Map.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).containsEntry("title", "Resource already exists");
    }

    @Test
    void should_update_device_and_assign_location() {
        ResponseEntity<Map> location = rest.postForEntity("/api/locations",
                Map.of("name", "Lab 1", "description", "Ground floor"), Map.class);
        assertThat(location.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String locationId = (String) location.getBody().get("id");

        rest.postForEntity("/api/devices",
                Map.of("deviceId", "chamber-loc", "name", "Chamber"), Map.class);

        ResponseEntity<Map> updated = rest.exchange("/api/devices/chamber-loc",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(
                        Map.of("name", "Chamber renamed", "locationId", locationId)),
                Map.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).containsEntry("name", "Chamber renamed");
        assertThat(updated.getBody()).containsEntry("locationId", locationId);
    }

    @Test
    void should_delete_device() {
        rest.postForEntity("/api/devices",
                Map.of("deviceId", "chamber-del", "name", "Doomed"), Map.class);

        ResponseEntity<Void> deleted = rest.exchange("/api/devices/chamber-del",
                org.springframework.http.HttpMethod.DELETE, null, Void.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.getForEntity("/api/devices/chamber-del", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
