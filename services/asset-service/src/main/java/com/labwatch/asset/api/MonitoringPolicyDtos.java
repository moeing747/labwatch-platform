package com.labwatch.asset.api;

import com.labwatch.asset.domain.Metric;
import com.labwatch.asset.domain.MonitoringPolicy;
import com.labwatch.asset.domain.Severity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class MonitoringPolicyDtos {

    private MonitoringPolicyDtos() {
    }

    public record CreatePolicyRequest(
            @NotNull Metric metric,
            @NotNull BigDecimal minimum,
            @NotNull BigDecimal maximum,
            @NotNull @Positive Integer violationDurationSeconds,
            @NotNull Severity severity
    ) {
    }

    public record UpdatePolicyRequest(
            @NotNull BigDecimal minimum,
            @NotNull BigDecimal maximum,
            @NotNull @Positive Integer violationDurationSeconds,
            @NotNull Severity severity
    ) {
    }

    public record PolicyResponse(
            UUID id,
            String deviceId,
            Metric metric,
            BigDecimal minimum,
            BigDecimal maximum,
            int violationDurationSeconds,
            Severity severity,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static PolicyResponse from(MonitoringPolicy policy) {
            return new PolicyResponse(policy.getId(), policy.getDevice().getDeviceId(), policy.getMetric(),
                    policy.getMinimum(), policy.getMaximum(), policy.getViolationDurationSeconds(),
                    policy.getSeverity(), policy.getCreatedAt(), policy.getUpdatedAt());
        }
    }
}
