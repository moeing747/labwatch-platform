package com.labwatch.monitoring.streams;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.Topics;
import com.labwatch.contracts.policy.DevicePoliciesPayload;
import com.labwatch.contracts.telemetry.DeviceTelemetryPayload;
import java.time.Clock;
import java.util.Set;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

/**
 * device.telemetry.v1 (stream) joined with device.policy-updated.v1 (table,
 * latest policy snapshot per device), evaluated by the stateful
 * ViolationDetector, violations emitted to monitoring.violations.v1.
 *
 * All topics are keyed by deviceId, so the join is co-partitioned and each
 * device's events are processed in order by a single stream task.
 */
@Configuration
@EnableKafkaStreams
public class MonitoringTopology {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MonitoringTopology(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Bean
    public KStream<String, String> violationStream(StreamsBuilder builder) {
        var telemetryEnvelopeSerde = new JsonSerde<>(objectMapper,
                new TypeReference<EventEnvelope<DeviceTelemetryPayload>>() {
                });
        var policiesEnvelopeSerde = new JsonSerde<>(objectMapper,
                new TypeReference<EventEnvelope<DevicePoliciesPayload>>() {
                });

        KTable<String, EventEnvelope<DevicePoliciesPayload>> policies = builder.table(
                Topics.DEVICE_POLICY_UPDATED_V1,
                Consumed.with(Serdes.String(), policiesEnvelopeSerde),
                Materialized.as("device-policies"));

        KStream<String, String> violations = builder
                .stream(Topics.DEVICE_TELEMETRY_V1, Consumed.with(Serdes.String(), telemetryEnvelopeSerde))
                .join(policies, (telemetry, policyEnvelope) ->
                        new JoinedTelemetry(telemetry, policyEnvelope.payload()))
                .process(detectorSupplier());

        violations.to(Topics.MONITORING_VIOLATIONS_V1, Produced.with(Serdes.String(), Serdes.String()));
        return violations;
    }

    private ProcessorSupplier<String, JoinedTelemetry, String, String> detectorSupplier() {
        var stateSerde = new JsonSerde<>(objectMapper, new TypeReference<ViolationState>() {
        });
        StoreBuilder<?> storeBuilder = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(ViolationDetector.STORE_NAME), Serdes.String(), stateSerde);

        return new ProcessorSupplier<>() {
            @Override
            public Processor<String, JoinedTelemetry, String, String> get() {
                return new ViolationDetector(objectMapper, clock);
            }

            @Override
            public Set<StoreBuilder<?>> stores() {
                return Set.of(storeBuilder);
            }
        };
    }
}
