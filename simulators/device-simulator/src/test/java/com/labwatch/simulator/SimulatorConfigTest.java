package com.labwatch.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SimulatorConfigTest {

    @Test
    void should_parse_all_arguments() {
        SimulatorConfig config = SimulatorConfig.fromArgs(new String[]{
                "--target=http://telemetry:8082", "--devices=10", "--rate=2.5",
                "--failure-profile=temperature-drift", "--duration-seconds=60", "--seed=7"});

        assertThat(config.target()).isEqualTo("http://telemetry:8082");
        assertThat(config.devices()).isEqualTo(10);
        assertThat(config.eventsPerSecond()).isEqualTo(2.5);
        assertThat(config.failureProfile()).isEqualTo(FailureProfile.TEMPERATURE_DRIFT);
        assertThat(config.durationSeconds()).isEqualTo(60);
        assertThat(config.seed()).isEqualTo(7);
    }

    @Test
    void should_apply_defaults_when_no_arguments_given() {
        SimulatorConfig config = SimulatorConfig.fromArgs(new String[0]);

        assertThat(config.devices()).isEqualTo(5);
        assertThat(config.failureProfile()).isEqualTo(FailureProfile.NORMAL);
        assertThat(config.durationSeconds()).isZero();
    }

    @Test
    void should_reject_malformed_argument() {
        assertThatThrownBy(() -> SimulatorConfig.fromArgs(new String[]{"devices=5"}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
