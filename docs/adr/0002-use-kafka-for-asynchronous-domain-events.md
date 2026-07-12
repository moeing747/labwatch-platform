# ADR 0002: Use Kafka for Asynchronous Domain Events

## Status

Accepted

## Context

The platform receives high-volume telemetry and has multiple independent consumers for monitoring, storage, analytics, auditing, and notifications.

## Decision

Use Apache Kafka for asynchronous telemetry and domain events.

Use REST for synchronous commands, configuration, and queries.

## Rationale

Kafka provides:

- Durable event retention
- Independent consumers
- Replay capability
- Horizontal consumer scaling
- Partition-based ordering

## Consequences

Positive:

- Consumers are decoupled
- Telemetry can be replayed
- Services can scale independently

Negative:

- Operational complexity increases
- Event contracts require disciplined versioning
- Consumers must handle duplicate delivery

## Rule

> REST manages synchronous commands and configuration. Kafka carries asynchronous device events and derived domain events.
