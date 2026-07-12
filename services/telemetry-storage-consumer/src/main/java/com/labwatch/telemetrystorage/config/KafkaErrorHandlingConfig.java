package com.labwatch.telemetrystorage.config;

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
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Transient failures (database or network outages) are retried with backoff;
 * permanent failures (malformed payloads) go straight to the dead-letter topic.
 * The recoverer appends failure metadata headers: original topic, partition,
 * offset, exception class and message, and failure timestamp.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafkaTemplate) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000);
        backOff.setMultiplier(2.0);

        // Our DLT naming is <topic>.dlt (see docs/reliability.md); Spring's default suffix is -dlt.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".dlt", record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JacksonException.class);
        return handler;
    }

    @Bean
    public NewTopic deviceTelemetryDltTopic() {
        return TopicBuilder.name(Topics.DEVICE_TELEMETRY_V1 + ".dlt").partitions(3).replicas(1).build();
    }
}
