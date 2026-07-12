package com.labwatch.simulator;

public enum FailureProfile {
    NORMAL,
    TEMPERATURE_DRIFT;

    public static FailureProfile fromArg(String value) {
        return valueOf(value.toUpperCase().replace('-', '_'));
    }
}
