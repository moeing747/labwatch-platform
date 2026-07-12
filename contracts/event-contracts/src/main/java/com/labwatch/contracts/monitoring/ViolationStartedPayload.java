package com.labwatch.contracts.monitoring;

import com.labwatch.contracts.policy.Metric;
import com.labwatch.contracts.policy.Severity;
import java.math.BigDecimal;
import java.time.Instant;

/** Payload of <METRIC>_VIOLATION_STARTED events on monitoring.violations.v1. */
public record ViolationStartedPayload(
        String deviceId,
        Metric metric,
        BigDecimal measuredValue,
        BigDecimal threshold,
        Instant violationStartedAt,
        Instant detectedAt,
        Severity severity
) {
}
