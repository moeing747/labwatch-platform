package com.labwatch.monitoring.streams;

import java.time.Instant;

/**
 * Per device+metric state held in the Streams state store.
 *
 * A state exists while measurements are out of range. {@code notified} flips to
 * true once the violation-started event has been emitted, which is what makes
 * "exactly one started event per sustained violation" hold across many
 * out-of-range readings.
 */
public record ViolationState(Instant startedAt, boolean notified) {
}
