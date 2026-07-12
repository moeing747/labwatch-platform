package com.labwatch.asset.config;

import com.labwatch.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Topics are created explicitly; broker auto-creation is disabled (see coding guidelines). */
@Configuration
public class KafkaTopicConfig {

    /**
     * Compacted: the topic carries the latest policy snapshot per device, so Kafka
     * only needs to retain the most recent record for each key.
     */
    @Bean
    public NewTopic devicePolicyUpdatedTopic() {
        return TopicBuilder.name(Topics.DEVICE_POLICY_UPDATED_V1)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .build();
    }
}
