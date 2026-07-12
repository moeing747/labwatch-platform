package com.labwatch.simulator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Random;

/**
 * Generates telemetry values per device. Deterministic for a given seed,
 * so test scenarios are reproducible.
 *
 * NORMAL: temperature stays inside the typical 2-8 range around a 5.0 base.
 * TEMPERATURE_DRIFT: temperature climbs steadily and crosses the 8.0 threshold
 * after a few minutes, producing sustained violations.
 * SUDDEN_SPIKE: short 30-second spikes above the threshold every 5 minutes,
 * which a correctly configured monitoring policy must NOT turn into violations.
 */
public class TelemetryGenerator {

    private static final double BASE_TEMPERATURE = 5.0;
    private static final double DRIFT_PER_MINUTE = 0.5;
    private static final double SPIKE_TEMPERATURE = 10.0;
    private static final long SPIKE_PERIOD_SECONDS = 300;
    private static final long SPIKE_LENGTH_SECONDS = 30;

    private final FailureProfile profile;
    private final Random random;

    public TelemetryGenerator(FailureProfile profile, long seed) {
        this.profile = profile;
        this.random = new Random(seed);
    }

    public record Reading(BigDecimal temperature, BigDecimal humidity) {
    }

    public Reading next(int deviceIndex, Duration elapsed) {
        double noise = random.nextGaussian() * 0.4;
        double temperature = switch (profile) {
            case NORMAL -> BASE_TEMPERATURE + noise;
            case TEMPERATURE_DRIFT -> BASE_TEMPERATURE + noise
                    + DRIFT_PER_MINUTE * elapsed.toSeconds() / 60.0
                    + deviceIndex * 0.1;
            case SUDDEN_SPIKE -> elapsed.toSeconds() % SPIKE_PERIOD_SECONDS < SPIKE_LENGTH_SECONDS
                    ? SPIKE_TEMPERATURE + noise
                    : BASE_TEMPERATURE + noise;
        };
        double humidity = clamp(55.0 + random.nextGaussian() * 5.0, 0.0, 100.0);
        return new Reading(toDecimal(temperature), toDecimal(humidity));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static BigDecimal toDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
