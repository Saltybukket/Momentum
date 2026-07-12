# ADR-014: Reliable database-outbox delivery

- Status: Accepted
- Date: 2026-07-12

## Context

Domain mutations and outbox rows were committed atomically, but synchronous in-process dispatch happened after the commit. A handler failure could therefore turn a durable success into HTTP `500`, while no durable processor retried the event.

## Decision

HTTP success ends at the durable database commit. Request handlers never dispatch persisted events synchronously. `outbox_events` is the sole delivery source and follows `PENDING -> PROCESSING -> PROCESSED`, with retryable `FAILED` and terminal `DEAD_LETTER` states.

Workers atomically claim bounded due batches. PostgreSQL uses row locks with `SKIP LOCKED`; SQLite provides deterministic single-process development/test behavior and makes no production-concurrency claim. A claim records `claim_owner` and a five-minute lease. Expired `PROCESSING` leases are reclaimable. Failures increment `attempts`, store only a bounded error type, and schedule exponential backoff capped at one hour. Five failed attempts dead-letter an event. Event IDs and the in-process dispatcher provide handler deduplication within a process; all production side-effect handlers must additionally be idempotent because crash recovery is at-least-once.

`make outbox-process` runs one bounded batch and prints machine-readable counts. A scheduler or process supervisor must invoke it continuously in deployed environments.

## Consequences

A committed mutation can no longer be reported as failed because a downstream handler failed. Multiple PostgreSQL workers do not hold the same active claim concurrently, stale claims recover after crashes, and poison events cannot loop forever. External brokers remain replaceable future adapters rather than a requirement for this gate.
