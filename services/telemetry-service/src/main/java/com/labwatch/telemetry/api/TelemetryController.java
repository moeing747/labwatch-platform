package com.labwatch.telemetry.api;

import com.labwatch.telemetry.api.TelemetryDtos.TelemetryRequest;
import com.labwatch.telemetry.api.TelemetryDtos.TelemetryResponse;
import com.labwatch.telemetry.application.TelemetryIngestionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryIngestionService ingestionService;

    public TelemetryController(TelemetryIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<TelemetryResponse> ingest(
            @Valid @RequestBody TelemetryRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId) {
        UUID effectiveCorrelationId = correlationId == null ? UUID.randomUUID() : correlationId;
        try {
            MDC.put("correlationId", String.valueOf(effectiveCorrelationId));
            MDC.put("deviceId", request.deviceId());
            var envelope = ingestionService.ingest(request, effectiveCorrelationId);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new TelemetryResponse(envelope.eventId(), envelope.correlationId()));
        } finally {
            MDC.clear();
        }
    }
}
