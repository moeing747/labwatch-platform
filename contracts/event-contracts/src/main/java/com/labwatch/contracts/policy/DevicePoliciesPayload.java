package com.labwatch.contracts.policy;

import java.util.List;

/**
 * Payload of DEVICE_MONITORING_POLICY_UPDATED events on device.policy-updated.v1.
 *
 * Carries the device's complete current policy set (not a delta), so the topic
 * acts as a state stream keyed by deviceId: consumers always hold the latest
 * full picture, and a deleted policy is simply absent from the next snapshot.
 */
public record DevicePoliciesPayload(
        String deviceId,
        List<MonitoringPolicySnapshot> policies
) {
}
