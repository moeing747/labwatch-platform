package com.labwatch.incident.config;

import com.labwatch.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the topics this service touches so startup order does not matter;
 * creation is idempotent and broker auto-creation is disabled.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic monitoringViolationsTopic() {
        return TopicBuilder.name(Topics.MONITORING_VIOLATIONS_V1).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic incidentEventsTopic() {
        return TopicBuilder.name(Topics.INCIDENT_EVENTS_V1).partitions(3).replicas(1).build();
    }
}
