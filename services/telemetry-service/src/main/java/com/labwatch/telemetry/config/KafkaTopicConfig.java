package com.labwatch.telemetry.config;

import com.labwatch.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Topics are created explicitly; broker auto-creation is disabled (see coding guidelines). */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic deviceTelemetryTopic() {
        return TopicBuilder.name(Topics.DEVICE_TELEMETRY_V1)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
