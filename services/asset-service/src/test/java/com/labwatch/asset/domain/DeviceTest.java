package com.labwatch.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T12:00:00Z");

    @Test
    void should_create_device_with_generated_id_and_timestamps() {
        Device device = Device.create("chamber-042", "Cold chamber 42", null, NOW);

        assertThat(device.getId()).isNotNull();
        assertThat(device.getDeviceId()).isEqualTo("chamber-042");
        assertThat(device.getName()).isEqualTo("Cold chamber 42");
        assertThat(device.getLocationId()).isNull();
        assertThat(device.getCreatedAt()).isEqualTo(NOW);
        assertThat(device.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void should_update_name_and_location_and_touch_updated_at() {
        Device device = Device.create("chamber-042", "Cold chamber 42", null, NOW);
        UUID locationId = UUID.randomUUID();
        Instant later = NOW.plusSeconds(60);

        device.update("Renamed chamber", locationId, later);

        assertThat(device.getName()).isEqualTo("Renamed chamber");
        assertThat(device.getLocationId()).isEqualTo(locationId);
        assertThat(device.getCreatedAt()).isEqualTo(NOW);
        assertThat(device.getUpdatedAt()).isEqualTo(later);
    }
}
