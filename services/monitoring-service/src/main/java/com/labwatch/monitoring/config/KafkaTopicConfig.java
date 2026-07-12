package com.labwatch.monitoring.config;

import com.labwatch.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares all topics this service touches so startup order does not matter;
 * creation is idempotent and broker auto-creation is disabled.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic deviceTelemetryTopic() {
        return TopicBuilder.name(Topics.DEVICE_TELEMETRY_V1).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic devicePolicyUpdatedTopic() {
        return TopicBuilder.name(Topics.DEVICE_POLICY_UPDATED_V1).partitions(3).replicas(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT).build();
    }

    @Bean
    public NewTopic monitoringViolationsTopic() {
        return TopicBuilder.name(Topics.MONITORING_VIOLATIONS_V1).partitions(3).replicas(1).build();
    }
}
