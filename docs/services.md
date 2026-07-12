# Service Responsibilities

## Asset Service

### Responsibilities

- Device CRUD
- Equipment metadata
- Measurement configuration
- Monitoring threshold policies
- Device-to-location assignment

### Example Endpoints

```http
POST /api/devices
GET /api/devices/{deviceId}
PUT /api/devices/{deviceId}

POST /api/devices/{deviceId}/monitoring-policies
GET /api/devices/{deviceId}/monitoring-policies
```

### Example Monitoring Policy

```json
{
  "metric": "TEMPERATURE",
  "minimum": 2.0,
  "maximum": 8.0,
  "violationDurationSeconds": 180,
  "severity": "HIGH"
}
```

### Suggested Tables

```text
devices
locations
monitoring_policies
```

### Learning Objectives

- Spring Boot fundamentals
- REST design
- Bean Validation
- Spring Data JPA
- PostgreSQL
- Flyway migrations
- Exception handling
- Unit and repository tests

---

## Telemetry Service

### Responsibilities

- Accept telemetry
- Validate payloads
- Reject invalid timestamps
- Normalize measurement units
- Generate event IDs
- Generate correlation IDs
- Publish telemetry events to Kafka

### Endpoint

```http
POST /api/telemetry
```

### Example Java Event Contract

```java
public record DeviceTelemetryReceived(
    UUID eventId,
    String deviceId,
    Instant occurredAt,
    BigDecimal temperature,
    BigDecimal humidity,
    DeviceOperatingState operatingState
) {}
```

Use Java records for immutable event contracts.

### Output Topic

```text
device.telemetry.v1
```

### Message Key

```text
deviceId
```

---

## Telemetry Storage Consumer

### Responsibilities

- Consume telemetry events
- Persist historical telemetry to PostgreSQL
- Deduplicate replayed or redelivered events

### Input Topic

```text
device.telemetry.v1
```

### Suggested Tables

```text
telemetry_readings
```

### Learning Objectives

- Kafka consumer groups
- Idempotent persistence
- Integration tests with Kafka and PostgreSQL containers

---

## Monitoring Service

### Responsibilities

- Consume telemetry events
- Consume monitoring-policy updates
- Join telemetry with current policy state
- Detect sustained threshold violations
- Prevent duplicate violation events
- Detect recovery
- Produce monitoring events

### Input Topics

```text
device.telemetry.v1
device.policy-updated.v1
```

### Output Topic

```text
monitoring.violations.v1
```

Violation-started and violation-resolved events share this topic, keyed by `deviceId`, so consumers see the violation lifecycle in order.

### Example Rule

```text
Temperature > configured maximum
for longer than configured duration
→ emit violation event
```

### Advanced Topics

Add after the core implementation works:

- Tumbling windows
- Sliding windows
- Grace periods
- Event-time processing
- Out-of-order events
- Stateful aggregation

---

## Incident Service

### Responsibilities

- Create incidents from monitoring events
- Prevent duplicate incidents
- Manage incident lifecycle
- Add operator notes
- Preserve incident history
- Publish incident lifecycle events

### Endpoints

```http
GET /api/incidents
GET /api/incidents/{incidentId}
POST /api/incidents/{incidentId}/acknowledge
POST /api/incidents/{incidentId}/resolve
```

### Domain Model

```java
public enum IncidentStatus {
    OPEN,
    ACKNOWLEDGED,
    INVESTIGATING,
    RESOLVED
}
```

### Suggested Tables

```text
incidents
incident_history
outbox_events
```

### Layering

```text
Controller
    ↓
Application Service
    ↓
Domain Model
    ↓
Repository
```

Do not expose JPA entities directly through the REST API.

---

## Notification Service

### Responsibilities

- Consume notification commands
- Send email or webhook notifications
- Track delivery state
- Retry transient failures
- Publish delivery results

The initial implementation can use a mock email or webhook adapter.

### Input Topic

```text
notification.commands.v1
```

### Suggested Delivery States

```text
PENDING
SENT
FAILED
RETRYING
```

---

## Device Simulator

### Responsibilities

- Simulate configurable numbers of devices
- Generate realistic telemetry
- Inject temperature drift
- Inject intermittent connectivity
- Produce deterministic test scenarios

### Example CLI

```bash
java -jar device-simulator.jar \
  --devices=500 \
  --rate=10 \
  --failure-profile=temperature-drift
```

### Suggested Failure Profiles

- Normal operation
- Temperature drift
- Sudden temperature spike
- Humidity threshold violation
- Intermittent connectivity
- Out-of-order telemetry
- Duplicate event delivery
