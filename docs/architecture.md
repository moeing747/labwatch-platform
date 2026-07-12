# Architecture

## System Context

LabWatch is an event-driven platform for monitoring connected laboratory and industrial equipment.

Devices publish telemetry. The platform validates and normalizes incoming measurements, evaluates them against monitoring policies, opens or resolves incidents, and exposes operational state through REST APIs.

## High-Level Architecture

```text
┌─────────────────────┐
│ Device Simulator    │
│ Java CLI            │
└──────────┬──────────┘
           │
           │ REST telemetry
           ▼
┌─────────────────────┐
│ Telemetry Service   │
│ Spring Boot         │
│ Validation          │
│ Normalization       │
└──────────┬──────────┘
           │
           │ device.telemetry.v1
           ▼
┌──────────────────────────────────┐
│ Kafka                            │
└───────┬──────────┬───────────────┘
        │          │
        │          │
        ▼          ▼
┌──────────────┐  ┌───────────────────┐
│ Monitoring   │  │ Telemetry Storage │
│ Kafka Streams│  │ Consumer          │
│ Time Windows │  │ Spring Boot       │
│              │  │ PostgreSQL        │
└──────┬───────┘  └───────────────────┘
       │
       │ monitoring.violations.v1
       ▼
┌─────────────────────┐
│ Incident Service    │
│ Spring Boot         │
│ PostgreSQL          │
│ REST API            │
└──────────┬──────────┘
           │
           │ incident.events.v1
           ▼
┌─────────────────────┐
│ Notification        │
│ Email/Webhook mock  │
└─────────────────────┘
```

## Architectural Boundaries

### Synchronous Flows

Use REST for:

- Device CRUD
- Location management
- Monitoring policy management
- Incident queries
- Incident state transitions
- Administrative commands

### Asynchronous Flows

Use Kafka for:

- Device telemetry
- Policy update propagation
- Monitoring violations
- Incident lifecycle events
- Audit events
- Notification commands

## Core Design Rule

> REST manages synchronous commands and configuration. Kafka carries asynchronous device events and derived domain events.

## Event Partitioning

Use `deviceId` as the Kafka message key for telemetry and monitoring events.

Benefits:

- Maintains ordering for events from the same device
- Enables partition-based horizontal scaling
- Keeps device-specific state local to one stream task

Potential downside:

- A small number of high-volume devices can create hot partitions

This is acceptable for the initial implementation and can later be revisited with a more advanced partitioning strategy if required.

## Stateful Monitoring

The Monitoring Service is responsible for evaluating sustained violations.

Example:

```text
temperature > maximum
        │
        ▼
Start violation timer
        │
        ▼
Still invalid after 3 minutes?
        │
    ┌───┴───┐
    │       │
   No      Yes
    │       │
Discard    Emit violation
```

The stream-processing layer must account for:

- Event-time processing
- Delayed events
- Out-of-order events
- Configurable grace periods
- Duplicate events
- Recovery events

Do not implement all advanced cases in the first iteration.

## Incident State Model

```text
OPEN
  ↓
ACKNOWLEDGED
  ↓
INVESTIGATING
  ↓
RESOLVED
```

The Incident Service owns state transition rules.

Each valid transition produces an immutable event.

## Data Ownership

Each service owns its domain data.

Recommended boundaries:

| Service | Owned Data |
|---|---|
| Asset Service | Devices, locations, monitoring policies |
| Telemetry Service | Ingestion metadata and optional raw-event persistence |
| Telemetry Storage Consumer | Historical telemetry data |
| Monitoring Service | Stream state and violation state |
| Incident Service | Incidents, incident history, outbox records |
| Notification Service | Delivery attempts and notification status |

For local development, services may share one PostgreSQL server while using separate schemas or databases.

## Deployment Model

Initial deployment:

```text
Docker Compose
├── Kafka
├── PostgreSQL
├── Asset Service
├── Telemetry Service
├── Telemetry Storage Consumer
├── Monitoring Service
├── Incident Service
├── Device Simulator
├── Prometheus
└── Grafana
```

Kubernetes is intentionally excluded from the initial scope.

## Non-Goals

The first version does not include:

- Kubernetes
- Elasticsearch
- GraphQL
- Redis
- Keycloak
- Event sourcing
- CQRS frameworks
- AI anomaly detection
- Multiple database technologies

These technologies may be added only when a concrete requirement justifies them.
