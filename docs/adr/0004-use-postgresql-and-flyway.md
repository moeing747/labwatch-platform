# ADR 0004: Use PostgreSQL and Flyway

## Status

Accepted

## Context

The platform requires relational data, transactional consistency, constraints, audit history, and repeatable schema evolution.

## Decision

Use PostgreSQL as the relational database and Flyway for schema migrations.

## Rationale

- Strong transactional guarantees
- Mature indexing and query capabilities
- Good fit for incident history and configuration data
- Widely used in Java backend systems
- Flyway keeps schema changes versioned and reproducible

## Consequences

Positive:

- Reliable relational persistence
- Explicit migration history
- Consistent local and CI environments

Negative:

- Database-specific integration tests are required
- H2 cannot fully represent PostgreSQL behavior

Use PostgreSQL Testcontainers for integration tests.
