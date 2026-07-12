# Reliability and Event Processing

## Reliability Goals

The system must:

- Avoid duplicate incidents
- Preserve valid event ordering per device
- Retry transient failures
- Isolate poison messages
- Maintain traceability through correlation IDs
- Avoid losing domain events during database updates

## Event Identity

Every event must include a globally unique `eventId`.

Example:

```json
{
  "eventId": "cf9894e2-5d74-469f-b66f-f57499f581fd"
}
```

Consumers persist processed event IDs or use a domain-specific deduplication key.

## Idempotent Consumers

Consumer processing must be safe when Kafka redelivers an event.

Example:

```text
Receive violation event
        ↓
Check processed event ID
        ↓
Already processed?
   ┌────┴────┐
   │         │
  Yes       No
   │         │
Ignore   Process and record
```

The Incident Service must not create multiple incidents from the same violation event.

## Retry Strategy

Separate transient and permanent failures.

Transient examples:

- Temporary database outage
- Temporary network failure
- Downstream timeout

Permanent examples:

- Invalid schema
- Unsupported event version
- Missing required identifiers

Use bounded retries with backoff for transient failures.

## Dead-Letter Topics

Route unrecoverable messages to a DLT.

Examples:

```text
device.telemetry.v1.dlt
monitoring.violations.v1.dlt
incident.events.v1.dlt
```

Include failure metadata:

- Original topic
- Original partition
- Original offset
- Exception class
- Exception message
- Failure timestamp
- Consumer group

## Correlation IDs

A correlation ID connects all processing steps initiated by one request or event chain.

Example flow:

```text
Telemetry request
      ↓
Telemetry event
      ↓
Violation event
      ↓
Incident event
      ↓
Notification command
```

All logs and events should carry the same `correlationId` where possible.

## Structured Logging

Example:

```json
{
  "timestamp": "2026-07-12T14:20:00Z",
  "level": "INFO",
  "service": "incident-service",
  "correlationId": "7cc8...",
  "deviceId": "chamber-042",
  "eventId": "cf9894e2...",
  "message": "Incident created"
}
```

## Transactional Outbox

Problem:

```text
Database commit succeeds
Kafka publish fails
```

Without an outbox, the service can persist state but fail to publish the corresponding event.

Solution:

```text
Database transaction
       │
       ├── Update incident
       │
       └── Insert outbox event
```

A separate publisher reads pending outbox events and publishes them to Kafka.

Recommended outbox fields:

```text
id
event_id
aggregate_type
aggregate_id
event_type
payload
created_at
published_at
status
```

## Optimistic Locking

Use optimistic locking for concurrent incident updates.

Example:

```java
@Version
private long version;
```

This prevents silent lost updates when two operators update the same incident concurrently.

## Health Endpoints

Expose:

```text
/actuator/health/liveness
/actuator/health/readiness
```

Readiness should reflect critical dependencies required to process traffic.

## Delivery Semantics

Do not claim global exactly-once processing.

Use precise language:

- At-least-once delivery
- Idempotent consumers
- Transactional read-process-write where applicable
- Exactly-once semantics only within clearly defined Kafka transactional boundaries
