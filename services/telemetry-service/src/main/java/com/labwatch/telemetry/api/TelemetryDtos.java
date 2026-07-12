package com.labwatch.telemetry.api;

import com.labwatch.contracts.telemetry.DeviceOperatingState;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TelemetryDtos {

    private TelemetryDtos() {
    }

    public record TelemetryRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9][a-z0-9-]*", message = "must be lowercase alphanumeric with dashes")
            String deviceId,
            @NotNull
            Instant timestamp,
            @NotNull @DecimalMin("-100.0") @DecimalMax("200.0")
            BigDecimal temperature,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0")
            BigDecimal humidity,
            @NotNull
            DeviceOperatingState operatingState
    ) {
    }

    public record TelemetryResponse(
            UUID eventId,
            UUID correlationId
    ) {
    }
}
