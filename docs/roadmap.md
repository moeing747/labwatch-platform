# Development Roadmap

## Phase 1 — Spring Fundamentals

Build:

```text
asset-service
```

Features:

- Spring Boot
- REST CRUD
- Bean Validation
- Spring Data JPA
- PostgreSQL
- Flyway
- Global exception handling
- Unit tests
- Repository tests

Goal:

Learn Spring fundamentals before adding Kafka complexity.

Exit criteria:

- Devices can be created, queried, updated, and deleted.
- Monitoring policies can be assigned to devices.
- Validation errors return consistent problem responses.
- Database migrations run automatically.
- Core domain behavior is covered by tests.

---

## Phase 2 — Kafka Producer and Consumer

Build:

```text
telemetry-service
telemetry-storage-consumer
```

Flow:

```text
POST telemetry
      ↓
Kafka
      ↓
Consumer
      ↓
PostgreSQL
```

Features:

- Kafka producer
- Kafka consumer
- JSON event serialization
- Correlation IDs
- Message keys
- Consumer groups
- Integration tests with Kafka and PostgreSQL containers

Exit criteria:

- Valid telemetry is published to Kafka.
- Invalid telemetry is rejected.
- Telemetry is persisted by an independent consumer.
- Integration tests use real Kafka and PostgreSQL containers.

---

## Phase 3 — Monitoring Engine

Build:

```text
monitoring-service
```

Implement:

```text
Temperature > threshold
for longer than configured duration
→ violation event
```

Features:

- Kafka Streams topology
- Policy lookup
- Stateful violation tracking
- Violation-started events
- Violation-resolved events
- Duplicate suppression

Exit criteria:

- Short spikes do not create violations.
- Sustained violations create exactly one violation-started event.
- Recovery creates one violation-resolved event.
- Tests cover time-based behavior.

---

## Phase 4 — Incident Workflow

Build:

```text
incident-service
```

Features:

- Consume monitoring violations
- Create incidents
- Enforce valid state transitions
- Add operator notes
- Expose incident REST API
- Persist incident history

Exit criteria:

- A violation creates one incident.
- Duplicate events do not create duplicate incidents.
- Invalid state transitions are rejected.
- Every state change is recorded.

---

## Phase 5 — Reliability

Add:

- Retry handling
- Dead-letter topics
- Consumer idempotency
- Event IDs
- Correlation IDs
- Structured logs
- Health endpoints

Example structured log:

```json
{
  "timestamp": "2026-07-12T14:20:00Z",
  "level": "INFO",
  "service": "incident-service",
  "correlationId": "7cc8...",
  "deviceId": "chamber-042",
  "message": "Incident created"
}
```

Exit criteria:

- Transient failures are retried.
- Poison messages are routed to a DLT.
- Duplicate events are safely ignored.
- Health endpoints expose readiness and liveness.

---

## Phase 6 — Transactional Outbox

Implement the outbox pattern in the Incident Service.

```text
Database transaction
       │
       ├── Update incident
       │
       └── Insert outbox event
```

Then publish the outbox event to Kafka.

Problem addressed:

```text
Database commit succeeds
Kafka publish fails
```

Exit criteria:

- Incident state and outbox record are committed atomically.
- Outbox events are published reliably.
- Published outbox records are marked or removed safely.

Do not start with this phase. Build it after the ordinary event flow works.

---

## Phase 7 — Observability

Add:

```text
Spring Boot Actuator
Micrometer
Prometheus
Grafana
```

Metrics:

```text
Telemetry events per second
Kafka consumer lag
Incident creation count
Processing latency
Failed event count
```

Exit criteria:

- Prometheus scrapes service metrics.
- Grafana displays core operational dashboards.
- Logs contain correlation IDs.

---

## Advanced Features

### Telemetry Replay

Endpoint:

```http
POST /api/replay
```

Purpose:

Reprocess historical telemetry after monitoring rules change.

### Simulation Profiles

```bash
java -jar device-simulator.jar \
  --devices=500 \
  --rate=10 \
  --failure-profile=temperature-drift
```

### Out-of-Order Events

Demonstrate controlled handling of delayed telemetry.

### Idempotent Consumers

Sending the same Kafka event twice must not create two incidents.

### Consumer Scaling

```bash
docker compose up \
  --scale monitoring-service=3
```

### Exactly-Once Stream Processing

Treat this as an advanced final feature, not an MVP requirement.

---

## Explicit Non-Goals

Do not add these in the initial implementation:

- Kubernetes
- Elasticsearch
- GraphQL
- Redis
- Keycloak
- Event sourcing
- CQRS frameworks
- AI anomaly detection
- Multiple database technologies
- Excessive microservice decomposition

Every technology must solve a concrete problem rather than serve as a résumé keyword.
