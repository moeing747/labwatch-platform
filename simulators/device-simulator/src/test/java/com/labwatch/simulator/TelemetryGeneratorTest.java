package com.labwatch.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TelemetryGeneratorTest {

    @Test
    void should_stay_near_base_range_when_profile_is_normal() {
        TelemetryGenerator generator = new TelemetryGenerator(FailureProfile.NORMAL, 42);

        for (int i = 0; i < 200; i++) {
            TelemetryGenerator.Reading reading = generator.next(0, Duration.ofMinutes(30));
            assertThat(reading.temperature()).isBetween(new BigDecimal("2.0"), new BigDecimal("8.0"));
            assertThat(reading.humidity()).isBetween(BigDecimal.ZERO, new BigDecimal("100.0"));
        }
    }

    @Test
    void should_cross_upper_threshold_when_profile_is_temperature_drift() {
        TelemetryGenerator generator = new TelemetryGenerator(FailureProfile.TEMPERATURE_DRIFT, 42);

        TelemetryGenerator.Reading afterTenMinutes = generator.next(0, Duration.ofMinutes(10));

        assertThat(afterTenMinutes.temperature()).isGreaterThan(new BigDecimal("8.0"));
    }

    @Test
    void should_spike_briefly_then_return_to_normal_when_profile_is_sudden_spike() {
        TelemetryGenerator generator = new TelemetryGenerator(FailureProfile.SUDDEN_SPIKE, 42);

        TelemetryGenerator.Reading duringSpike = generator.next(0, Duration.ofSeconds(10));
        TelemetryGenerator.Reading afterSpike = generator.next(0, Duration.ofSeconds(60));

        assertThat(duringSpike.temperature()).isGreaterThan(new BigDecimal("8.0"));
        assertThat(afterSpike.temperature()).isLessThan(new BigDecimal("8.0"));
    }

    @Test
    void should_be_deterministic_for_same_seed() {
        TelemetryGenerator first = new TelemetryGenerator(FailureProfile.NORMAL, 7);
        TelemetryGenerator second = new TelemetryGenerator(FailureProfile.NORMAL, 7);

        assertThat(first.next(0, Duration.ZERO)).isEqualTo(second.next(0, Duration.ZERO));
    }
}
