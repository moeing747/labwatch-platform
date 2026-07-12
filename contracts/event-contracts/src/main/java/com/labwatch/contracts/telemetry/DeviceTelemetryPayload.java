package com.labwatch.contracts.telemetry;

import java.math.BigDecimal;

/** Payload of DEVICE_TELEMETRY_RECEIVED events on device.telemetry.v1. */
public record DeviceTelemetryPayload(
        String deviceId,
        BigDecimal temperature,
        BigDecimal humidity,
        DeviceOperatingState operatingState
) {
}
