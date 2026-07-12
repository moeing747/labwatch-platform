# ADR 0005: Defer Nonessential Infrastructure

## Status

Accepted

## Context

The project is intended to demonstrate production-oriented Java, Spring Boot, Kafka, PostgreSQL, and testing competence.

Adding infrastructure without concrete requirements would dilute the learning objective.

## Decision

Exclude the following from the initial implementation:

- Kubernetes
- Elasticsearch
- GraphQL
- Redis
- Keycloak
- Event sourcing
- CQRS frameworks
- AI anomaly detection
- Multiple database technologies

## Rationale

Every technology must solve a demonstrated problem.

## Consequences

Positive:

- Lower implementation noise
- Clearer architecture
- Faster delivery of a complete system
- Easier explanation during interviews

Negative:

- Some infrastructure topics remain outside the initial portfolio scope

Those technologies may be added later only when a concrete requirement justifies them.
