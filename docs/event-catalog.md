# Event Catalog

## Naming Convention

Use versioned topic names:

```text
<domain>.<event-or-stream>.v<version>
```

Examples:

```text
device.telemetry.v1
monitoring.violations.v1
incident.events.v1
```

## Topic Catalog

| Topic | Producer | Consumers |
|---|---|---|
| `device.telemetry.v1` | Telemetry Service | Monitoring Service, Telemetry Storage Consumer |
| `device.policy-updated.v1` | Asset Service | Monitoring Service |
| `monitoring.violations.v1` | Monitoring Service | Incident Service |
| `incident.events.v1` | Incident Service | Audit Consumer, Notification Service |
| `notification.commands.v1` | Incident Service | Notification Service |
| `*.dlt` | Failed Consumers | Operations |

Start with only:

```text
device.telemetry.v1
monitoring.violations.v1
incident.events.v1
```

Split topics only when event semantics or consumer requirements justify it.

Lifecycle events of the same entity stay in one topic. Kafka guarantees ordering only within a partition of a single topic, so keeping `violation-started` and `violation-resolved` events in `monitoring.violations.v1` with `deviceId` as the key ensures consumers always see them in order. The `eventType` envelope field discriminates between them.

## Common Event Envelope

Recommended envelope:

```json
{
  "eventId": "9a50b6a8-36ab-4db7-b61c-2e1f1f3b3a22",
  "eventType": "DEVICE_TELEMETRY_RECEIVED",
  "eventVersion": 1,
  "occurredAt": "2026-07-12T12:10:30Z",
  "producedAt": "2026-07-12T12:10:31Z",
  "correlationId": "c850568b-7d79-48de-8db5-094f847f0f90",
  "producer": "telemetry-service",
  "payload": {}
}
```

Required metadata:

- `eventId`
- `eventType`
- `eventVersion`
- `occurredAt`
- `producedAt`
- `correlationId`
- `producer`

## Device Telemetry Event

Topic:

```text
device.telemetry.v1
```

Message key:

```text
deviceId
```

Example:

```json
{
  "eventId": "cf9894e2-5d74-469f-b66f-f57499f581fd",
  "eventType": "DEVICE_TELEMETRY_RECEIVED",
  "eventVersion": 1,
  "occurredAt": "2026-07-12T12:10:30Z",
  "producedAt": "2026-07-12T12:10:31Z",
  "correlationId": "4856cb43-e9f0-4dc3-a8e3-d48748316db3",
  "producer": "telemetry-service",
  "payload": {
    "deviceId": "chamber-042",
    "temperature": 9.4,
    "humidity": 61.2,
    "operatingState": "RUNNING"
  }
}
```

## Policy Updated Event

Topic:

```text
device.policy-updated.v1
```

Message key: `deviceId`. The topic is compacted and the payload carries the
device's complete current policy set (not a delta), so consumers can treat it
as a state stream: the latest record per device is the full truth, and a
deleted policy is simply absent from the next snapshot.

Example:

```json
{
  "eventType": "DEVICE_MONITORING_POLICY_UPDATED",
  "payload": {
    "deviceId": "chamber-042",
    "policies": [
      {
        "metric": "TEMPERATURE",
        "minimum": 2.0,
        "maximum": 8.0,
        "violationDurationSeconds": 180,
        "severity": "HIGH"
      }
    ]
  }
}
```

## Violation Started Event

Topic:

```text
monitoring.violations.v1
```

Example:

```json
{
  "eventType": "TEMPERATURE_VIOLATION_STARTED",
  "payload": {
    "deviceId": "chamber-042",
    "metric": "TEMPERATURE",
    "measuredValue": 9.4,
    "threshold": 8.0,
    "violationStartedAt": "2026-07-12T12:07:00Z",
    "detectedAt": "2026-07-12T12:10:00Z",
    "severity": "HIGH"
  }
}
```

## Violation Resolved Event

Topic:

```text
monitoring.violations.v1
```

Example:

```json
{
  "eventType": "TEMPERATURE_VIOLATION_RESOLVED",
  "payload": {
    "deviceId": "chamber-042",
    "metric": "TEMPERATURE",
    "recoveredValue": 7.8,
    "resolvedAt": "2026-07-12T12:22:00Z"
  }
}
```

## Incident Events

Topic:

```text
incident.events.v1
```

Possible event types:

```text
INCIDENT_OPENED
INCIDENT_ACKNOWLEDGED
INCIDENT_INVESTIGATION_STARTED
INCIDENT_RESOLVED
INCIDENT_NOTE_ADDED
```

Example:

```json
{
  "eventType": "INCIDENT_OPENED",
  "payload": {
    "incidentId": "inc-8821",
    "deviceId": "chamber-042",
    "severity": "HIGH",
    "status": "OPEN",
    "reason": "TEMPERATURE_ABOVE_LIMIT"
  }
}
```

## Event Compatibility Rules

- Treat event contracts as public APIs.
- Prefer additive schema changes.
- Do not silently rename or remove fields.
- Introduce a new version for breaking changes.
- Consumers must ignore unknown fields.
- Producers should not depend on consumer implementation details.
