package com.labwatch.simulator;

import java.util.HashMap;
import java.util.Map;

/** Parsed CLI configuration, e.g. --devices=5 --rate=10 --failure-profile=temperature-drift */
public record SimulatorConfig(
        String target,
        int devices,
        double eventsPerSecond,
        FailureProfile failureProfile,
        long durationSeconds,
        long seed
) {

    public static SimulatorConfig fromArgs(String[] args) {
        Map<String, String> values = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("Invalid argument: " + arg + " (expected --key=value)");
            }
            String[] parts = arg.substring(2).split("=", 2);
            values.put(parts[0], parts[1]);
        }
        return new SimulatorConfig(
                values.getOrDefault("target", "http://localhost:8082"),
                Integer.parseInt(values.getOrDefault("devices", "5")),
                Double.parseDouble(values.getOrDefault("rate", "5")),
                FailureProfile.fromArg(values.getOrDefault("failure-profile", "normal")),
                Long.parseLong(values.getOrDefault("duration-seconds", "0")),
                Long.parseLong(values.getOrDefault("seed", "42")));
    }
}
