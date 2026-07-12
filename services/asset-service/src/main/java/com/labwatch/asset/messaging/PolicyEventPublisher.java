package com.labwatch.asset.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.EventTypes;
import com.labwatch.contracts.Topics;
import com.labwatch.contracts.policy.DevicePoliciesPayload;
import com.labwatch.contracts.policy.Metric;
import com.labwatch.contracts.policy.MonitoringPolicySnapshot;
import com.labwatch.contracts.policy.Severity;
import com.labwatch.asset.domain.MonitoringPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes the device's complete current policy set after every policy change.
 *
 * Publishing is asynchronous and best-effort: a Kafka outage must not fail the
 * REST request. The lost-event window this leaves is addressed by the
 * transactional outbox in roadmap Phase 6.
 */
@Component
public class PolicyEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PolicyEventPublisher.class);
    private static final String PRODUCER_NAME = "asset-service";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PolicyEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, Clock clock) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void publishPolicySnapshot(String deviceId, List<MonitoringPolicy> policies) {
        Instant now = Instant.now(clock);
        DevicePoliciesPayload payload = new DevicePoliciesPayload(deviceId,
                policies.stream().map(PolicyEventPublisher::toSnapshot).toList());
        EventEnvelope<DevicePoliciesPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID(), EventTypes.DEVICE_MONITORING_POLICY_UPDATED, 1,
                now, now, UUID.randomUUID(), PRODUCER_NAME, payload);
        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(Topics.DEVICE_POLICY_UPDATED_V1, deviceId, json)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            log.error("Failed to publish policy snapshot for device {}", deviceId, exception);
                        }
                    });
        } catch (JsonProcessingException exception) {
            log.error("Failed to serialize policy snapshot for device {}", deviceId, exception);
        }
    }

    private static MonitoringPolicySnapshot toSnapshot(MonitoringPolicy policy) {
        return new MonitoringPolicySnapshot(
                Metric.valueOf(policy.getMetric().name()),
                policy.getMinimum(),
                policy.getMaximum(),
                policy.getViolationDurationSeconds(),
                Severity.valueOf(policy.getSeverity().name()));
    }
}
