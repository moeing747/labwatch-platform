# ADR 0003: Use a Maven Multi-Module Monorepo

## Status

Accepted

## Context

The project contains multiple Spring services, shared event contracts, infrastructure definitions, and a device simulator.

## Decision

Use one Git repository with a Maven multi-module structure.

## Rationale

- Simplifies local development
- Keeps documentation and infrastructure versioned together
- Makes refactoring easier during early development
- Reduces repository-management overhead
- Improves portfolio discoverability

## Consequences

Positive:

- One clone starts the complete system
- Shared build conventions are easier to maintain
- CI configuration is centralized

Negative:

- Independent service release workflows require extra build logic
- Shared repository changes can affect multiple modules

This is acceptable for the project scope.
