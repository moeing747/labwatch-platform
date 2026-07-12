# Coding Guidelines

## Guiding Principle

Write simple, straightforward, boring code.

A reader should understand any class without framework archaeology. Every rule below exists to serve that principle. When a rule and simplicity conflict, prefer simplicity and record the deviation.

## Simplicity Rules

- No interface with a single implementation. Extract an interface when the second implementation arrives. A concretely planned second implementation counts: the Notification Service delivery adapter starts as an interface because the mock adapter is designed to be replaced by real email and webhook adapters.
- No abstraction before the second concrete use.
- No Lombok. Java records cover DTOs, events, and value objects; the hand-written getters and setters on the few JPA entities are an accepted cost of dependency-free, readable code.
- No mapping frameworks. The mapping surface is a handful of DTO pairs per service; hand-written mapper methods document the boundary and need no build-time magic.
- No shared `common` or `util` module for speculative reuse. The `event-contracts` module is not this: it is shared deliberately because it defines the public contract between services.
- No configuration switches for behavior nobody requested.
- Implement only the current roadmap phase. Do not build ahead.
- Prefer a clear loop over a clever stream chain.
- Architecturally significant deviations require a new ADR before the code lands: introducing a technology, breaking a layering or data-ownership rule, or violating a documented non-goal. Small judgment calls do not need an ADR — they need a clear name and, at most, a code comment stating the constraint.

## Java

- Java 21.
- Use records for event contracts, DTOs, and value objects.
- Make classes immutable unless mutation is the point (JPA entities).
- Use `BigDecimal` for measurements. Never `double` for domain values.
- Use `Instant` in UTC for all timestamps. Inject `java.time.Clock` instead of calling `Instant.now()` directly, so time is testable.
- Use `Optional` only as a return type, never as a field or parameter.
- Validate at boundaries with Bean Validation; inside the domain, reject illegal states in constructors.

## Spring

- Constructor injection only. No field injection, no setter injection.
- One `@RestControllerAdvice` per service returning RFC 9457 `ProblemDetail` responses.
- Bind configuration with `@ConfigurationProperties` records, not scattered `@Value`.
- Controllers stay thin: validate, delegate to an application service, map to a response DTO.
- Never expose JPA entities through the REST API or Kafka. Map to records at the boundary.
- Avoid Spring profiles beyond `default` and `test`.

## Package Structure

One consistent layout per service:

```text
com.labwatch.<service>
├── api            Controllers, request/response records, exception handler
├── application    Use-case services, transaction boundaries
├── domain         Entities, value objects, state rules, repository interfaces
├── messaging      Kafka producers, consumers, event records
└── config         Spring configuration
```

Dependencies point inward: `api` and `messaging` depend on `application`, `application` depends on `domain`. `domain` depends on nothing above it.

## Kafka

- Topic names come only from the [event catalog](event-catalog.md). Define them as constants in the `event-contracts` module. Never inline topic strings.
- Message key is `deviceId` for telemetry and monitoring events.
- Every event uses the common envelope with all required metadata fields.
- Lifecycle events of one entity share one topic, discriminated by `eventType` (see event catalog).
- JSON serialization. Consumers must ignore unknown fields (`FAIL_ON_UNKNOWN_PROPERTIES = false`).
- Producers: `acks=all`, idempotence enabled.
- Consumer group IDs follow `<service>.<purpose>`, e.g. `incident-service.violation-consumer`.
- Topics are created explicitly (Compose init or `KafkaAdmin` beans), never by auto-creation.
- Retries, backoff, and DLT routing follow [reliability.md](reliability.md) and arrive in Phase 5. Do not preempt them.
- Consumers are idempotent from the start: check the event ID or a natural key before acting (see [reliability.md](reliability.md)).

## Persistence

- Flyway owns the schema. `spring.jpa.hibernate.ddl-auto=validate`, never `update`.
- Migration naming: `V<number>__<snake_case_description>.sql`. Never edit an applied migration.
- One PostgreSQL schema per service (shared local server is fine, see [architecture.md](architecture.md)).
- Primary keys are UUIDs generated in the application.
- Use `@Version` optimistic locking on entities operators can update concurrently.
- Spring Data JPA repositories. Write `@Query` only when a derived query name becomes unreadable.

## Errors and Logging

- Distinguish transient from permanent failures as defined in [reliability.md](reliability.md).
- Structured JSON logs with the required fields from [observability.md](observability.md).
- Propagate `correlationId` through every log line and every produced event.
- No `printStackTrace`, no swallowed exceptions, no `catch (Exception e) { log; }` without rethrow or explicit handling decision.

## Testing

[testing-strategy.md](testing-strategy.md) is authoritative. Operational rules:

- Test names: `should_<expected_behavior>_when_<condition>`.
- Unit tests for domain logic run without Spring context.
- Integration tests use PostgreSQL and Kafka Testcontainers. No H2. No mocked Kafka for integration behavior.
- Reuse containers per module (singleton pattern) to keep the suite fast.
- Use Awaitility for asynchronous assertions. Never `Thread.sleep`.
- Every consumer gets an idempotency test: the same event twice must not act twice.

## Build

- Maven multi-module monorepo (ADR 0003). Versions and shared plugin configuration live only in the parent POM.
- Modules: `services/*`, `simulators/device-simulator`, `contracts/event-contracts`.
- `mvn verify` from the root must always pass. Integration tests run in the `verify` phase via Failsafe (`*IT` classes); unit tests via Surefire (`*Test` classes).

## Commits

- Subject lines follow [Conventional Commits](https://www.conventionalcommits.org/): `<type>(<scope>)?: <description>`.
- Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
- Subject is imperative mood, at most 72 characters.
- Enforced locally by `.githooks/commit-msg` (enable once with `git config core.hooksPath .githooks`) and in CI on every push.

## Scope Guard

The non-goals in [architecture.md](architecture.md) and ADR 0005 are binding. Do not introduce Kubernetes, Elasticsearch, GraphQL, Redis, Keycloak, event sourcing, CQRS frameworks, AI anomaly detection, or additional database technologies without a new ADR that names the concrete problem being solved.

## Definition of Done

A change is done when:

- The behavior is covered by tests at the appropriate layer.
- Schema changes ship as a Flyway migration.
- New events or topics are recorded in the [event catalog](event-catalog.md).
- Affected documentation is updated in the same change.
- Nothing outside the current phase was built.
