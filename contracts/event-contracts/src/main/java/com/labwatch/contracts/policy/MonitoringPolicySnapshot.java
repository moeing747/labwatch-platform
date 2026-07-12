package com.labwatch.contracts.policy;

import java.math.BigDecimal;

public record MonitoringPolicySnapshot(
        Metric metric,
        BigDecimal minimum,
        BigDecimal maximum,
        int violationDurationSeconds,
        Severity severity
) {
}
