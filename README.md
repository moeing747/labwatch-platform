# LabWatch Platform

Event-driven monitoring and incident-management platform for connected laboratory and industrial equipment, built with Java, Spring Boot, Apache Kafka, Kafka Streams, PostgreSQL, and Docker.

## What It Does

Connected devices continuously report telemetry such as temperature, humidity, operating state, and connectivity. LabWatch:

- Ingests, validates, and normalizes device telemetry
- Evaluates measurements against configurable monitoring policies
- Detects sustained threshold violations while ignoring transient spikes and sensor noise
- Opens incidents and tracks them through an operator workflow
- Maintains a complete, replayable operational audit trail

Example: a cold-storage chamber is configured for 2°C–8°C with a three-minute violation duration. A single reading of 9.4°C is ignored; three sustained minutes above 8°C open a HIGH-severity incident that operators move through its lifecycle:

```text
OPEN → ACKNOWLEDGED → INVESTIGATING → RESOLVED
```

## How It Works

```text
Devices ──REST──▶ Telemetry Service ──▶ Kafka ──▶ Monitoring Service ──▶ Incident Service ──▶ Notification Service
                                          │        (Kafka Streams)
                                          └──▶ Telemetry Storage Consumer ──▶ PostgreSQL
```

REST handles synchronous commands and configuration; Kafka carries asynchronous device events and derived domain events, keyed by `deviceId` for per-device ordering. See [Architecture](docs/architecture.md) for the full picture.

## Tech Stack

Java 21 · Spring Boot 3.5 (Web, Validation, Data JPA, Kafka) · Kafka Streams · PostgreSQL · Flyway · Docker Compose · JUnit 5 · Testcontainers · Awaitility · OpenAPI · Micrometer · Prometheus · Grafana · GitHub Actions

## Getting Started

Prerequisites: JDK 21 or newer (`JAVA_HOME` must point to it) and Docker. The Maven Wrapper downloads Maven automatically.

```bash
git config core.hooksPath .githooks   # enable the commit-message hook (once)
./mvnw verify                         # build and test all modules
docker compose up -d                  # start Kafka, PostgreSQL, the services, Prometheus, and Grafana
```

Grafana serves the provisioned dashboards at [localhost:3000](http://localhost:3000) (no login required); Prometheus runs at [localhost:9090](http://localhost:9090).

To run the device simulation (5 devices with a temperature-drift profile posting telemetry through Kafka into PostgreSQL):

```bash
./mvnw package
docker compose --profile simulation up -d --build
docker exec labwatch-postgres psql -U labwatch -d labwatch \
  -c "SELECT device_id, count(*) FROM telemetry.telemetry_readings GROUP BY device_id;"
```

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/); see the [Coding Guidelines](docs/coding-guidelines.md).

## Status

Phases 0–7 are implemented: device and policy management (Asset Service), the telemetry pipeline (Telemetry Service, Kafka, storage consumer), the Kafka Streams monitoring engine detecting sustained threshold violations, the incident workflow with an idempotent violation consumer, reliability hardening (retries with backoff, dead-letter topics, structured JSON logs with correlation IDs, health probes), a transactional outbox in the Incident Service, and an observability stack (Micrometer metrics scraped by Prometheus, provisioned Grafana dashboards) — all running in Docker Compose with a device simulator. Advanced features follow per the [Roadmap](docs/roadmap.md) and [Implementation Plan](docs/implementation-plan.md).

## Repository Structure

```text
labwatch-platform/
│
├── services/
│   ├── asset-service/
│   ├── telemetry-service/
│   ├── telemetry-storage-consumer/
│   ├── monitoring-service/
│   ├── incident-service/
│   └── notification-service/
│
├── simulators/
│   └── device-simulator/
│
├── contracts/
│   └── event-contracts/
│
├── infrastructure/
│   ├── prometheus/
│   └── grafana/
│
├── docs/
│
├── docker-compose.yml
├── README.md
└── pom.xml
```

## Documentation

- [Architecture](docs/architecture.md)
- [Services](docs/services.md)
- [Event Catalog](docs/event-catalog.md)
- [Roadmap](docs/roadmap.md)
- [Implementation Plan](docs/implementation-plan.md)
- [Coding Guidelines](docs/coding-guidelines.md)
- [Testing Strategy](docs/testing-strategy.md)
- [Reliability](docs/reliability.md)
- [Observability](docs/observability.md)
- [Technology Decisions](docs/adr/)
