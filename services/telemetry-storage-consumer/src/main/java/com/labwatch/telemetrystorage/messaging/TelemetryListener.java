package com.labwatch.telemetrystorage.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.Topics;
import com.labwatch.contracts.telemetry.DeviceTelemetryPayload;
import com.labwatch.telemetrystorage.application.TelemetryStorageService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TelemetryListener {

    private static final TypeReference<EventEnvelope<DeviceTelemetryPayload>> TELEMETRY_EVENT =
            new TypeReference<>() {
            };

    private final TelemetryStorageService storageService;
    private final ObjectMapper objectMapper;

    public TelemetryListener(TelemetryStorageService storageService, ObjectMapper objectMapper) {
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.DEVICE_TELEMETRY_V1)
    public void onTelemetry(ConsumerRecord<String, String> record) throws JsonProcessingException {
        EventEnvelope<DeviceTelemetryPayload> envelope = objectMapper.readValue(record.value(), TELEMETRY_EVENT);
        try {
            MDC.put("correlationId", String.valueOf(envelope.correlationId()));
            MDC.put("eventId", String.valueOf(envelope.eventId()));
            MDC.put("deviceId", envelope.payload().deviceId());
            storageService.store(envelope);
        } finally {
            MDC.clear();
        }
    }
}
