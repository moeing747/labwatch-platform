# ADR 0001: Use Java 21 and Spring Boot 3

## Status

Accepted

## Context

The project must demonstrate modern Java and transferable Spring backend competence without introducing unnecessary migration complexity.

## Decision

Use:

```text
Java 21
Spring Boot 3.5.x
```

## Rationale

- Java 21 provides modern language features and long-term support.
- Spring Boot 3 is mature and widely transferable across current enterprise Java roles.
- The combination supports records, modern JVM performance, current Spring APIs, and production-oriented tooling.

## Consequences

Positive:

- Strong alignment with enterprise Java roles
- Modern Java syntax and APIs
- Broad compatibility with the Spring ecosystem

Negative:

- Does not demonstrate the newest Spring Boot major version

The trade-off is intentional. The objective is durable Spring competence, not framework-version novelty.
