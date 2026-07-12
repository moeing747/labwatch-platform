# Testing Strategy

## Principles

- Test domain behavior directly.
- Use real infrastructure for integration boundaries.
- Avoid making the full suite dependent on Mockito.
- Keep unit tests fast and deterministic.
- Test time-based stream behavior explicitly.
- Verify idempotency and failure handling.

## Unit Tests

Target:

- Domain services
- Validation rules
- State transitions
- Policy evaluation
- Mapping logic

Example:

```java
@Test
void shouldOpenViolationAfterConfiguredDuration() {
}
```

Recommended naming style:

```text
should_<expected_behavior>_when_<condition>
```

Examples:

```text
should_reject_policy_when_minimum_exceeds_maximum
should_acknowledge_open_incident
should_reject_resolution_of_already_resolved_incident
```

## Repository Tests

Use:

```java
@DataJpaTest
```

Test:

- Entity mappings
- Unique constraints
- Custom queries
- Optimistic locking
- Transactional behavior

Prefer PostgreSQL Testcontainers for database-specific behavior instead of relying exclusively on H2.

## REST Integration Tests

Use:

```java
@SpringBootTest
```

Test:

- Request validation
- HTTP status codes
- Error responses
- JSON serialization
- Database persistence

Example scenarios:

- Create a valid device
- Reject malformed device data
- Return `404` for unknown devices
- Reject invalid incident transitions

## Kafka Integration Tests

Use:

```text
Testcontainers Kafka
Testcontainers PostgreSQL
Awaitility
```

Test real producer and consumer behavior.

Example flow:

```text
Produce telemetry
      ↓
Wait for consumer processing
      ↓
Assert persisted state or emitted event
```

Avoid mocking Kafka for integration behavior.

## Kafka Streams Tests

Test:

- Short spikes do not open violations
- Sustained violations open exactly one violation
- Recovery closes active violations
- Duplicate input does not duplicate output
- Out-of-order events behave according to configured grace rules

Use a topology test driver for fast topology tests and Testcontainers for end-to-end verification.

## Idempotency Tests

Scenario:

```text
Publish event A
Publish event A again
```

Expected result:

```text
One incident
One history entry
No duplicate notification command
```

## Contract Tests

Validate event payloads against shared schemas or contract fixtures.

Rules:

- New fields must not break old consumers.
- Required fields must always be present.
- Breaking changes require a new event version.

## Failure Tests

Test:

- Kafka unavailable during publishing
- Consumer processing exception
- Database unavailable
- Poison messages
- Retry exhaustion
- DLT routing

## Performance Tests

Add after functional correctness.

Measure:

- Telemetry ingestion throughput
- End-to-end processing latency
- Consumer lag under load
- Incident creation latency

Example simulator command:

```bash
java -jar device-simulator.jar \
  --devices=500 \
  --rate=10
```

## CI Test Layers

Recommended pipeline:

```text
Compile
  ↓
Unit tests
  ↓
Static analysis
  ↓
Integration tests
  ↓
Package
  ↓
Container build
```
