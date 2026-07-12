package com.labwatch.contracts;

/** Event type discriminators carried in the envelope. */
public final class EventTypes {

    public static final String DEVICE_TELEMETRY_RECEIVED = "DEVICE_TELEMETRY_RECEIVED";
    public static final String DEVICE_MONITORING_POLICY_UPDATED = "DEVICE_MONITORING_POLICY_UPDATED";
    public static final String INCIDENT_OPENED = "INCIDENT_OPENED";
    public static final String INCIDENT_ACKNOWLEDGED = "INCIDENT_ACKNOWLEDGED";
    public static final String INCIDENT_INVESTIGATION_STARTED = "INCIDENT_INVESTIGATION_STARTED";
    public static final String INCIDENT_RESOLVED = "INCIDENT_RESOLVED";
    public static final String INCIDENT_NOTE_ADDED = "INCIDENT_NOTE_ADDED";

    /** e.g. TEMPERATURE_VIOLATION_STARTED */
    public static String violationStarted(String metric) {
        return metric + "_VIOLATION_STARTED";
    }

    /** e.g. TEMPERATURE_VIOLATION_RESOLVED */
    public static String violationResolved(String metric) {
        return metric + "_VIOLATION_RESOLVED";
    }

    private EventTypes() {
    }
}
