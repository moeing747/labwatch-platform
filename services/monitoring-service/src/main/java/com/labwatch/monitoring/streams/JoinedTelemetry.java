package com.labwatch.monitoring.streams;

import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.policy.DevicePoliciesPayload;
import com.labwatch.contracts.telemetry.DeviceTelemetryPayload;

/** A telemetry event paired with the device's current policy snapshot by the stream-table join. */
public record JoinedTelemetry(
        EventEnvelope<DeviceTelemetryPayload> telemetry,
        DevicePoliciesPayload policies
) {
}
