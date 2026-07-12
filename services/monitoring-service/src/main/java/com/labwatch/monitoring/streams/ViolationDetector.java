package com.labwatch.monitoring.streams;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.EventTypes;
import com.labwatch.contracts.monitoring.ViolationResolvedPayload;
import com.labwatch.contracts.monitoring.ViolationStartedPayload;
import com.labwatch.contracts.policy.MonitoringPolicySnapshot;
import com.labwatch.contracts.telemetry.DeviceTelemetryPayload;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stateful sustained-violation detection, driven by event time (occurredAt).
 *
 * For each telemetry event and each policy of the device:
 * - out of range, no state      -> start tracking (no event yet)
 * - out of range, sustained     -> emit exactly one <METRIC>_VIOLATION_STARTED
 * - back in range, notified     -> emit <METRIC>_VIOLATION_RESOLVED, clear state
 * - back in range, not notified -> short spike: clear state silently
 */
public class ViolationDetector implements Processor<String, JoinedTelemetry, String, String> {

    public static final String STORE_NAME = "violation-states";
    private static final String PRODUCER_NAME = "monitoring-service";
    private static final Logger log = LoggerFactory.getLogger(ViolationDetector.class);

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final Timer processingTimer;
    private ProcessorContext<String, String> context;
    private KeyValueStore<String, ViolationState> store;

    public ViolationDetector(ObjectMapper objectMapper, Clock clock, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.processingTimer = Timer.builder("labwatch.monitoring.processing.duration")
                .description("Time spent evaluating one telemetry event against its policies")
                .register(meterRegistry);
    }

    @Override
    public void init(ProcessorContext<String, String> context) {
        this.context = context;
        this.store = context.getStateStore(STORE_NAME);
    }

    @Override
    public void process(Record<String, JoinedTelemetry> record) {
        long start = System.nanoTime();
        EventEnvelope<DeviceTelemetryPayload> telemetry = record.value().telemetry();
        for (MonitoringPolicySnapshot policy : record.value().policies().policies()) {
            BigDecimal measured = measuredValue(telemetry.payload(), policy);
            if (measured != null) {
                evaluate(record, telemetry, policy, measured);
            }
        }
        processingTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }

    private void evaluate(Record<String, JoinedTelemetry> record, EventEnvelope<DeviceTelemetryPayload> telemetry,
                          MonitoringPolicySnapshot policy, BigDecimal measured) {
        String deviceId = telemetry.payload().deviceId();
        String stateKey = deviceId + "|" + policy.metric();
        ViolationState state = store.get(stateKey);
        Instant occurredAt = telemetry.occurredAt();
        boolean outOfRange = measured.compareTo(policy.minimum()) < 0 || measured.compareTo(policy.maximum()) > 0;

        if (outOfRange) {
            if (state == null) {
                store.put(stateKey, new ViolationState(occurredAt, false));
            } else if (!state.notified() && isSustained(state, occurredAt, policy)) {
                emitStarted(record, telemetry, policy, measured, state.startedAt(), occurredAt);
                store.put(stateKey, new ViolationState(state.startedAt(), true));
            }
        } else if (state != null) {
            if (state.notified()) {
                emitResolved(record, telemetry, policy, measured, occurredAt);
            }
            store.delete(stateKey);
        }
    }

    private static boolean isSustained(ViolationState state, Instant occurredAt, MonitoringPolicySnapshot policy) {
        return Duration.between(state.startedAt(), occurredAt)
                .compareTo(Duration.ofSeconds(policy.violationDurationSeconds())) >= 0;
    }

    private void emitStarted(Record<String, JoinedTelemetry> record, EventEnvelope<DeviceTelemetryPayload> telemetry,
                             MonitoringPolicySnapshot policy, BigDecimal measured,
                             Instant startedAt, Instant detectedAt) {
        BigDecimal threshold = measured.compareTo(policy.maximum()) > 0 ? policy.maximum() : policy.minimum();
        ViolationStartedPayload payload = new ViolationStartedPayload(
                telemetry.payload().deviceId(), policy.metric(), measured, threshold,
                startedAt, detectedAt, policy.severity());
        forward(record, EventTypes.violationStarted(policy.metric().name()), detectedAt, telemetry, payload);
        meterRegistry.counter("labwatch.violations.started",
                "metric", policy.metric().name(), "severity", policy.severity().name()).increment();
        log.info("Violation started: device={} metric={} value={} threshold={}",
                telemetry.payload().deviceId(), policy.metric(), measured, threshold);
    }

    private void emitResolved(Record<String, JoinedTelemetry> record, EventEnvelope<DeviceTelemetryPayload> telemetry,
                              MonitoringPolicySnapshot policy, BigDecimal measured, Instant resolvedAt) {
        ViolationResolvedPayload payload = new ViolationResolvedPayload(
                telemetry.payload().deviceId(), policy.metric(), measured, resolvedAt);
        forward(record, EventTypes.violationResolved(policy.metric().name()), resolvedAt, telemetry, payload);
        meterRegistry.counter("labwatch.violations.resolved",
                "metric", policy.metric().name(), "severity", policy.severity().name()).increment();
        log.info("Violation resolved: device={} metric={} value={}",
                telemetry.payload().deviceId(), policy.metric(), measured);
    }

    private void forward(Record<String, JoinedTelemetry> record, String eventType, Instant occurredAt,
                         EventEnvelope<DeviceTelemetryPayload> telemetry, Object payload) {
        EventEnvelope<Object> envelope = new EventEnvelope<>(
                UUID.randomUUID(), eventType, 1, occurredAt, Instant.now(clock),
                telemetry.correlationId(), PRODUCER_NAME, payload);
        try {
            context.forward(record.withValue(objectMapper.writeValueAsString(envelope)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize violation event", exception);
        }
    }

    private static BigDecimal measuredValue(DeviceTelemetryPayload payload, MonitoringPolicySnapshot policy) {
        return switch (policy.metric()) {
            case TEMPERATURE -> payload.temperature();
            case HUMIDITY -> payload.humidity();
        };
    }
}
