# ADR 0006: Use Exactly-Once Processing in the Monitoring Topology

## Status

Accepted

## Context

The monitoring service tracks violation state in a Kafka Streams state store.
The `notified` flag guarantees that one sustained violation emits exactly one
violation-started event.

Under `at_least_once` processing, the local state-store write and the emitted
output record are not atomic: a crash after the store write but before the
producer delivers the output persists `notified = true` while the event is
lost. Reprocessing then sees the flag and never re-emits. The deduplication
design converts the usual at-least-once failure mode (a duplicate) into
silent event loss — for a safety-monitoring system, the worst possible
failure direction. The technical review of 2026-07-12 identified this as the
highest-risk defect in the platform.

The roadmap originally deferred exactly-once processing to the advanced
backlog as a showcase feature. This defect makes it a correctness
requirement instead.

## Decision

Run the monitoring topology with `processing.guarantee: exactly_once_v2`.

## Rationale

- Kafka transactions make the state-store changelog write, the emitted
  violation event, and the input offset commit atomic — the loss window
  disappears.
- This stays within the delivery-semantics language of reliability.md:
  exactly-once only within a clearly defined Kafka transactional boundary
  (this topology). Downstream consumers keep deduplicating; events they
  consume remain at-least-once end to end.
- `exactly_once_v2` (KIP-447) has modest overhead (transactional commits per
  `commit.interval.ms`) acceptable for telemetry volumes.

## Consequences

Positive:

- One sustained violation emits exactly one started event even across crashes.

Negative:

- Slightly higher end-to-end latency (records visible to read_committed
  consumers only at transaction commit).
- Requires a broker with transaction support (any modern Kafka; single-node
  development setups need transaction-log replication factor 1, already
  configured in Docker Compose).
