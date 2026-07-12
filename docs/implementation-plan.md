# Implementation Plan

Operationalizes the [roadmap](roadmap.md). The roadmap defines phases and exit criteria; this plan defines concrete build order, versions, and working agreements. The [coding guidelines](coding-guidelines.md) apply to every step.

## Versions

```text
Java                21 (LTS)
Spring Boot         3.5.x
Apache Kafka        via Spring for Apache Kafka (Boot-managed version)
PostgreSQL          16
Testcontainers      Boot-managed version
Maven               3.9.x
```

Manage all versions in the parent POM through the Spring Boot BOM. Do not pin dependency versions in service modules.

## Working Agreements

1. One phase at a time. A phase starts only when the previous phase meets its exit criteria in the [roadmap](roadmap.md).
2. Every commit leaves `mvn verify` green from the repository root.
3. Documentation and the event catalog are updated in the same change that alters behavior or contracts.
4. Architecturally significant deviations from docs or ADRs require a new ADR before the code lands (see the [coding guidelines](coding-guidelines.md) for what counts as significant).

## Phase 0 — Bootstrap (not in the roadmap, do first)

Build the skeleton everything else hangs on:

- Parent `pom.xml` with Spring Boot BOM, Java 21, Surefire/Failsafe configuration
- `.gitignore`, `.editorconfig`
- Git repository initialization
- `docker-compose.yml` with Kafka and PostgreSQL only
- GitHub Actions workflow: compile + unit tests
- Empty module structure per the README repository layout

Done when: `mvn verify` passes, `docker compose up` starts Kafka and PostgreSQL, CI is green.

## Phase 1 — Asset Service

Roadmap Phase 1. Build order inside the service:

1. Module scaffold and Flyway baseline migration (`devices`, `locations`, `monitoring_policies`)
2. Domain model with validation rules (e.g. reject `minimum > maximum`)
3. Repositories with `@DataJpaTest` + PostgreSQL Testcontainers
4. Application services
5. REST API with request/response records and Bean Validation
6. Global exception handler returning `ProblemDetail`
7. OpenAPI via springdoc

No Kafka in this phase. Policy-update publishing is added in Phase 3 when a consumer exists.

## Phase 2 — Telemetry Pipeline

Roadmap Phase 2. Build order:

1. `event-contracts` module: common envelope record, telemetry event record, topic name constants
2. Telemetry Service: `POST /api/telemetry`, validation, normalization, publish to `device.telemetry.v1` keyed by `deviceId`
3. Telemetry Storage Consumer: consume, deduplicate by `eventId`, persist to `telemetry_readings`
4. Integration tests with Kafka + PostgreSQL Testcontainers and Awaitility
5. Device simulator, minimal version: N devices, fixed rate, normal profile only

## Phase 3 — Monitoring Engine

Roadmap Phase 3. Build order:

1. Asset Service publishes `device.policy-updated.v1` on policy changes
2. Kafka Streams topology: telemetry stream joined with policy table, stateful violation tracking
3. Emit violation-started and violation-resolved events to `monitoring.violations.v1` (single lifecycle topic, see [event catalog](event-catalog.md))
4. Topology tests with the topology test driver; one end-to-end Testcontainers test
5. Simulator gains `temperature-drift` and `sudden-spike` profiles to exercise the engine

Defer grace periods and out-of-order handling to the advanced backlog unless a test forces them.

## Phase 4 — Incident Workflow

Roadmap Phase 4. Build order:

1. Flyway migration: `incidents`, `incident_history`
2. Domain state machine enforcing OPEN → ACKNOWLEDGED → INVESTIGATING → RESOLVED
3. Idempotent consumer for `monitoring.violations.v1` (processed event IDs)
4. REST API for queries, transitions, and operator notes
5. Publish lifecycle events to `incident.events.v1`

## Phase 5 — Reliability

Roadmap Phase 5. Retrofit across services:

1. Bounded retries with backoff, DLT routing with failure metadata headers
2. Structured JSON logging with correlation IDs everywhere
3. Liveness and readiness endpoints wired into Compose healthchecks

## Phase 6 — Transactional Outbox

Roadmap Phase 6. Incident Service only: `outbox_events` table written in the incident transaction, a scheduled publisher, and a crash-safety integration test.

## Phase 7 — Observability

Roadmap Phase 7. Micrometer metrics named per [observability.md](observability.md), Prometheus + Grafana in Compose, the three dashboards from the observability doc.

## Advanced Backlog

Only after Phase 7, in this order of value: Notification Service (consume `notification.commands.v1` with a mock delivery adapter), telemetry replay, additional simulator failure profiles, out-of-order event handling, consumer scaling demonstration, exactly-once stream processing.
