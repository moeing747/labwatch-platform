package com.labwatch.incident.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.Topics;
import com.labwatch.contracts.monitoring.ViolationResolvedPayload;
import com.labwatch.contracts.monitoring.ViolationStartedPayload;
import com.labwatch.incident.application.ViolationEventHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ViolationListener {

    private static final Logger log = LoggerFactory.getLogger(ViolationListener.class);

    private static final TypeReference<EventEnvelope<ViolationStartedPayload>> STARTED_EVENT =
            new TypeReference<>() {
            };
    private static final TypeReference<EventEnvelope<ViolationResolvedPayload>> RESOLVED_EVENT =
            new TypeReference<>() {
            };

    private final ViolationEventHandler handler;
    private final ObjectMapper objectMapper;

    public ViolationListener(ViolationEventHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.MONITORING_VIOLATIONS_V1)
    public void onViolation(ConsumerRecord<String, String> record) throws JsonProcessingException {
        var root = objectMapper.readTree(record.value());
        String eventType = root.path("eventType").asText();
        try {
            MDC.put("correlationId", root.path("correlationId").asText());
            MDC.put("eventId", root.path("eventId").asText());
            MDC.put("deviceId", record.key());
            if (eventType.endsWith("_VIOLATION_STARTED")) {
                handler.onViolationStarted(objectMapper.readValue(record.value(), STARTED_EVENT));
            } else if (eventType.endsWith("_VIOLATION_RESOLVED")) {
                handler.onViolationResolved(objectMapper.readValue(record.value(), RESOLVED_EVENT));
            } else {
                log.warn("Ignoring unknown event type {} on {}", eventType, Topics.MONITORING_VIOLATIONS_V1);
            }
        } finally {
            MDC.clear();
        }
    }
}
