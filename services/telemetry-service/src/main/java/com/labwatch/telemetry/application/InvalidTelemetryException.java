package com.labwatch.telemetry.application;

public class InvalidTelemetryException extends RuntimeException {

    public InvalidTelemetryException(String message) {
        super(message);
    }
}
