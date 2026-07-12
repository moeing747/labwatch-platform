# Technical Review — 2026-07-12

Static analysis (SpotBugs, PMD) plus two independent deep reviews (Kafka/eventing
semantics; JPA/transactions/REST). Findings verified against code before recording.

## Fixed immediately (high risk — silent event loss)

1. **Monitoring topology could lose violation events on crash.** Under
   `at_least_once`, the `notified` state-store write and the emitted event were
   not atomic; a crash between them lost the event permanently (the dedup flag
   turned at-least-once's "duplicate" into "loss"). Fixed with
   `processing.guarantee: exactly_once_v2` — see ADR 0006.
2. **Retry budget dead-lettered valid events during routine outages.** Three
   retries (~7s) meant any real database restart sent every in-flight event to
   the DLT with no replay path. Fixed: transient failures now retry
   indefinitely with capped exponential backoff (consumption pauses until the
   dependency heals); only permanent failures (malformed payloads) go to the DLT.
3. **Deleting a device left its policies enforced forever.** The DB cascade
   removed policy rows without publishing a snapshot, so monitoring kept the
   last non-empty policy set and kept opening incidents for a deleted device.
   Fixed: `DeviceService.delete` publishes an empty snapshot; covered by IT.

## Open findings (accepted for now, ordered by priority)

### Medium

- **Policy snapshots publish before commit and without ordering.** Concurrent
  policy mutations on one device can produce snapshots whose topic order does
  not match commit order (a deleted policy can be resurrected in the compacted
  topic), and a commit failure after publish leaves Kafka diverged from the DB.
  Candidate fix: publish in a `TransactionSynchronization.afterCommit` hook and
  read the snapshot after commit, or route through an outbox like
  incident-service.
- **Violation events get fresh `eventId`s if the topology reprocesses.** State
  loss / offset reset re-emits semantically identical events with new IDs, which
  bypasses incident-service dedup (`processed_events`, `triggering_event_id`)
  and creates duplicate incidents. Candidate fix: derive the started-event ID
  deterministically (e.g. UUIDv5 of deviceId|metric|violationStartedAt).
- **Deleting a policy mid-violation strands detector state.** The state-store
  entry for the removed metric is never revisited: no RESOLVED event, the
  incident stays open, the state leaks. Candidate fix: clean up state for
  metrics absent from the current policy snapshot.
- **Storage consumer mislabels constraint violations as duplicates.** The
  `DataIntegrityViolationException` catch assumes "concurrent duplicate"; a
  null field or over-length value is silently dropped with a misleading log
  instead of reaching the DLT. Candidate fix: verify `existsByEventId` inside
  the catch; rethrow if it is not actually a duplicate.
- **Monitoring crash-loops on semantically null payloads.** A parseable record
  with `payload: null` passes the deserialization handler, then NPEs the stream
  thread on every restart. Candidate fix: null-guard in the topology and skip
  with a log/counter.
- **`KafkaTemplate.send()` can block ~60s inside open transactions.** During a
  broker outage the "async" publishers block on metadata inside `@Transactional`
  methods, exhausting the connection pool (asset-service) and servlet threads
  (telemetry-service). Candidate fix: afterCommit publishing plus a bounded
  `max.block.ms`.
- **Asset-service leaks 500s on unique-constraint races and numeric overflow.**
  Concurrent duplicate creates and `numeric(10,2)` overflow bypass the
  validation paths. Candidate fix: map `DataIntegrityViolationException` to 409,
  add `@Digits` bounds to policy DTOs.

### Low

- Unbounded `findAll` endpoints (incidents, devices) — add `Pageable` when data
  volume justifies it.
- OutboxPublisher is single-instance only (no `FOR UPDATE SKIP LOCKED`);
  fine for Compose, must be revisited before scaling out.
- Stream-table join with `max.task.idle.ms = 0` can evaluate telemetry against
  a stale policy during catch-up.
- Monitoring's serde skips malformed records with only a WARN — no DLT, no
  metric (revisit in Phase 7 with a skipped-records counter).
- `DevicePoliciesPayload` exposes its mutable list — `List.copyOf` in a compact
  constructor (only substantive static-analysis finding).
- Test gaps: `should_filter_incidents_by_status` doesn't assert exclusion;
  `TelemetryPersistenceIT` asserts on nondeterministic row order;
  `ViolationConsumerIT` only replays byte-identical duplicates.

## Static analysis verdict

PMD: zero P1–P3 violations. SpotBugs: only `EI_EXPOSE_REP2` noise on
constructor-injected dependencies (standard DI pattern) plus the payload-list
finding above. Given the codebase's conventions, permanent adoption is deferred;
testing-strategy.md's CI "static analysis" stage remains aspirational until a
tool earns its keep (Error Prone would be the first candidate).

## Verified sound (checked explicitly, no defect)

Offset-commit vs DB-transaction ordering in both DB consumers; outbox
write/rollback atomicity; topic declaration consistency and join
co-partitioning; optimistic-locking handling of concurrent operator updates;
DTO size limits vs column lengths; lazy-loading paths under `open-in-view=false`.
