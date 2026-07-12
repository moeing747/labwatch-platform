package com.labwatch.asset.api;

import com.labwatch.asset.domain.Device;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class DeviceDtos {

    private DeviceDtos() {
    }

    public record CreateDeviceRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9][a-z0-9-]*", message = "must be lowercase alphanumeric with dashes")
            String deviceId,
            @NotBlank @Size(max = 200)
            String name,
            UUID locationId
    ) {
    }

    public record UpdateDeviceRequest(
            @NotBlank @Size(max = 200)
            String name,
            UUID locationId
    ) {
    }

    public record DeviceResponse(
            UUID id,
            String deviceId,
            String name,
            UUID locationId,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static DeviceResponse from(Device device) {
            return new DeviceResponse(device.getId(), device.getDeviceId(), device.getName(),
                    device.getLocationId(), device.getCreatedAt(), device.getUpdatedAt());
        }
    }
}
