package com.labwatch.contracts;

/** Kafka topic names. The single source of truth is docs/event-catalog.md. */
public final class Topics {

    public static final String DEVICE_TELEMETRY_V1 = "device.telemetry.v1";
    public static final String DEVICE_POLICY_UPDATED_V1 = "device.policy-updated.v1";
    public static final String MONITORING_VIOLATIONS_V1 = "monitoring.violations.v1";

    private Topics() {
    }
}
