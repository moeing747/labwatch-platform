package com.labwatch.incident.config;

import com.fasterxml.jackson.core.JacksonException;
import com.labwatch.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Permanent failures (malformed payloads) go straight to the dead-letter topic.
 * Transient failures (database or network outages) are retried indefinitely
 * with capped exponential backoff: consumption pauses until the dependency
 * heals. Valid events must never be dead-lettered just because an outage
 * outlasted a retry budget - a DLT'd event has no replay path.
 * The recoverer appends failure metadata headers: original topic, partition,
 * offset, exception class and message, and failure timestamp.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafkaTemplate) {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000, 2.0);
        backOff.setMaxInterval(30_000);

        // Our DLT naming is <topic>.dlt (see docs/reliability.md); Spring's default suffix is -dlt.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".dlt", record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JacksonException.class);
        return handler;
    }

    @Bean
    public NewTopic monitoringViolationsDltTopic() {
        return TopicBuilder.name(Topics.MONITORING_VIOLATIONS_V1 + ".dlt").partitions(3).replicas(1).build();
    }
}
