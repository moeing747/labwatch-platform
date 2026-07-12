# Observability

## Stack

```text
Spring Boot Actuator
Micrometer
Prometheus
Grafana
Structured JSON logging
```

## Running

`docker compose up -d` starts Prometheus (<http://localhost:9090>) and Grafana
(<http://localhost:3000>, anonymous access) alongside the services. Prometheus
scrapes every service's `/actuator/prometheus` endpoint every 15 seconds with
one job per service, so the `job` label carries the service name. Configuration
lives in [infrastructure/prometheus](../infrastructure/prometheus/) and
[infrastructure/grafana](../infrastructure/grafana/); the three dashboards
below are provisioned automatically from
[infrastructure/grafana/dashboards](../infrastructure/grafana/dashboards/).

Metrics are registered in code without suffixes (for example
`labwatch.telemetry.received`); Micrometer's Prometheus naming convention adds
`_total` to counters and `_seconds` to timers, producing the names listed here.

## Metrics

### Telemetry Metrics

```text
labwatch_telemetry_received_total
labwatch_telemetry_rejected_total
labwatch_telemetry_publish_duration_seconds
```

Dimensions:

- Service
- Metric type
- Result

Avoid using high-cardinality identifiers such as `deviceId` as metric labels.

## Kafka Metrics

Monitor:

- Consumer lag
- Records consumed per second
- Records produced per second
- Processing latency
- Retry count
- DLT count

Potential dashboard panels:

```text
Telemetry events per second
Kafka consumer lag
Failed event count
Consumer processing latency
DLT messages by topic
```

## Incident Metrics

```text
labwatch_incidents_opened_total
labwatch_incidents_resolved_total
labwatch_incidents_open_current
labwatch_incident_resolution_duration_seconds
```

Dimensions:

- Severity
- Reason
- Status

## Monitoring Metrics

```text
labwatch_violations_started_total
labwatch_violations_resolved_total
labwatch_monitoring_processing_duration_seconds
```

## Service Health

Expose:

```text
/actuator/health
/actuator/health/liveness
/actuator/health/readiness
```

Expose metrics:

```text
/actuator/prometheus
```

## Logging

Use structured JSON logs.

Required fields:

```text
timestamp
level
service
message
correlationId
eventId
deviceId
incidentId
```

Only include identifiers when relevant.

Example:

```json
{
  "timestamp": "2026-07-12T14:20:00Z",
  "level": "INFO",
  "service": "incident-service",
  "correlationId": "7cc8...",
  "eventId": "cf9894e2...",
  "deviceId": "chamber-042",
  "incidentId": "inc-8821",
  "message": "Incident created"
}
```

## Tracing

Distributed tracing is optional for the initial version.

Add OpenTelemetry only after correlation IDs and basic metrics are implemented correctly.

Potential spans:

```text
telemetry.request
kafka.publish
monitoring.process
incident.create
notification.send
```

## Dashboard Structure

### System Overview

- Service health
- Event throughput
- Consumer lag
- Error rate
- Processing latency

### Monitoring Overview

- Active violations
- Violations by metric
- Violations by severity
- Detection latency

### Incident Overview

- Open incidents
- Incidents by severity
- Mean resolution time
- Incident state transitions

## Alerting Candidates

Add only after metrics are stable.

Examples:

- Consumer lag above threshold
- DLT growth
- Telemetry rejection spike
- Incident processing failures
- Service readiness failure
