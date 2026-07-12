package com.labwatch.contracts.monitoring;

import com.labwatch.contracts.policy.Metric;
import java.math.BigDecimal;
import java.time.Instant;

/** Payload of <METRIC>_VIOLATION_RESOLVED events on monitoring.violations.v1. */
public record ViolationResolvedPayload(
        String deviceId,
        Metric metric,
        BigDecimal recoveredValue,
        Instant resolvedAt
) {
}
