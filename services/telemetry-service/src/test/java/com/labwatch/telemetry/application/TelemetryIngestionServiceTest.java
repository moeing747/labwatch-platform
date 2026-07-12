package com.labwatch.telemetry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.labwatch.contracts.EventEnvelope;
import com.labwatch.contracts.EventTypes;
import com.labwatch.contracts.telemetry.DeviceOperatingState;
import com.labwatch.contracts.telemetry.DeviceTelemetryPayload;
import com.labwatch.telemetry.api.TelemetryDtos.TelemetryRequest;
import com.labwatch.telemetry.messaging.TelemetryEventPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TelemetryIngestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T12:00:00Z");

    private final TelemetryEventPublisher publisher = mock(TelemetryEventPublisher.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final TelemetryIngestionService service =
            new TelemetryIngestionService(publisher, meterRegistry, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void should_publish_envelope_with_normalized_values() {
        UUID correlationId = UUID.randomUUID();
        TelemetryRequest request = new TelemetryRequest("chamber-042", NOW.minusSeconds(1),
                new BigDecimal("9.4567"), new BigDecimal("61.234"), DeviceOperatingState.RUNNING);

        EventEnvelope<DeviceTelemetryPayload> envelope = service.ingest(request, correlationId);

        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.eventType()).isEqualTo(EventTypes.DEVICE_TELEMETRY_RECEIVED);
        assertThat(envelope.eventVersion()).isEqualTo(1);
        assertThat(envelope.occurredAt()).isEqualTo(NOW.minusSeconds(1));
        assertThat(envelope.producedAt()).isEqualTo(NOW);
        assertThat(envelope.correlationId()).isEqualTo(correlationId);
        assertThat(envelope.producer()).isEqualTo("telemetry-service");
        assertThat(envelope.payload().temperature()).isEqualByComparingTo("9.46");
        assertThat(envelope.payload().humidity()).isEqualByComparingTo("61.23");
        verify(publisher).publish(envelope);
    }

    @Test
    void should_reject_telemetry_when_timestamp_is_in_the_future() {
        TelemetryRequest request = new TelemetryRequest("chamber-042", NOW.plusSeconds(120),
                new BigDecimal("5.0"), new BigDecimal("50.0"), DeviceOperatingState.RUNNING);

        assertThatThrownBy(() -> service.ingest(request, UUID.randomUUID()))
                .isInstanceOf(InvalidTelemetryException.class)
                .hasMessageContaining("future");
        verify(publisher, never()).publish(any());
    }

    @Test
    void should_count_received_telemetry_when_ingestion_succeeds() {
        TelemetryRequest request = new TelemetryRequest("chamber-042", NOW.minusSeconds(1),
                new BigDecimal("5.0"), new BigDecimal("50.0"), DeviceOperatingState.RUNNING);

        service.ingest(request, UUID.randomUUID());

        assertThat(meterRegistry.counter("labwatch.telemetry.received").count()).isEqualTo(1.0);
    }

    @Test
    void should_not_count_received_telemetry_when_ingestion_is_rejected() {
        TelemetryRequest request = new TelemetryRequest("chamber-042", NOW.plusSeconds(120),
                new BigDecimal("5.0"), new BigDecimal("50.0"), DeviceOperatingState.RUNNING);

        assertThatThrownBy(() -> service.ingest(request, UUID.randomUUID()))
                .isInstanceOf(InvalidTelemetryException.class);

        assertThat(meterRegistry.counter("labwatch.telemetry.received").count()).isZero();
    }

    @Test
    void should_accept_timestamp_within_clock_skew_tolerance() {
        TelemetryRequest request = new TelemetryRequest("chamber-042", NOW.plusSeconds(10),
                new BigDecimal("5.0"), new BigDecimal("50.0"), DeviceOperatingState.RUNNING);

        EventEnvelope<DeviceTelemetryPayload> envelope = service.ingest(request, UUID.randomUUID());

        assertThat(envelope.occurredAt()).isEqualTo(NOW.plusSeconds(10));
    }
}
