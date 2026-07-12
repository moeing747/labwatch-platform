package com.labwatch.asset.api;

import com.labwatch.asset.domain.Location;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class LocationDtos {

    private LocationDtos() {
    }

    public record CreateLocationRequest(
            @NotBlank @Size(max = 100)
            String name,
            @Size(max = 500)
            String description
    ) {
    }

    public record LocationResponse(
            UUID id,
            String name,
            String description,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static LocationResponse from(Location location) {
            return new LocationResponse(location.getId(), location.getName(), location.getDescription(),
                    location.getCreatedAt(), location.getUpdatedAt());
        }
    }
}
