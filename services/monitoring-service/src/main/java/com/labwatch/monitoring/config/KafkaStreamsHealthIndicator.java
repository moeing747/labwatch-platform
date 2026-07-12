package com.labwatch.monitoring.config;

import org.apache.kafka.streams.KafkaStreams;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;

/**
 * Readiness is tied to the Streams state: the service only processes traffic
 * while the topology is RUNNING (or rebalancing back into it).
 */
@Component("kafkaStreams")
public class KafkaStreamsHealthIndicator implements HealthIndicator {

    private final StreamsBuilderFactoryBean streamsFactory;

    public KafkaStreamsHealthIndicator(StreamsBuilderFactoryBean streamsFactory) {
        this.streamsFactory = streamsFactory;
    }

    @Override
    public Health health() {
        KafkaStreams streams = streamsFactory.getKafkaStreams();
        if (streams == null) {
            return Health.down().withDetail("state", "NOT_STARTED").build();
        }
        KafkaStreams.State state = streams.state();
        boolean healthy = state == KafkaStreams.State.RUNNING || state == KafkaStreams.State.REBALANCING;
        return (healthy ? Health.up() : Health.down()).withDetail("state", state.name()).build();
    }
}
