# ADR-014: Reliable database-outbox delivery

- Status: Accepted
- Date: 2026-07-12

## Context

Domain mutations and outbox rows were committed atomically, but synchronous in-process dispatch happened after the commit. A handler failure could therefore turn a durable success into HTTP `500`, while no durable processor retried the event.

## Decision

HTTP success ends at the durable database commit. Request handlers never dispatch persisted events synchronously. `outbox_events` is the sole delivery source and follows `PENDING -> PROCESSING -> PROCESSED`, with retryable `FAILED` and terminal `DEAD_LETTER` states.

Workers process a bounded limit but claim each record just in time. PostgreSQL uses row locks with `SKIP LOCKED`; SQLite provides deterministic single-process development/test behavior and makes no production-concurrency claim. Every claim receives a unique UUID `claim_token` in addition to a safe process/run-specific `claim_owner`. Completion, failure and lease extension require both values, so an old claim—including one using the same worker name—cannot mutate a newer claim.

The five-minute lease is extended by a heartbeat while an async handler is active. The heartbeat stops on success, failure or cancellation; a process crash therefore still allows stale-lease recovery. This guarantee assumes handlers yield to the event loop and does not cover synchronously blocking code. Failures increment `attempts`, store only a bounded error type, and schedule exponential backoff capped at one hour. Five failed attempts dead-letter an event. Lost claims do not increment attempts and are reported separately.

Known event types must either have a registered handler or appear in an explicit informational no-op disposition set. The current five scaffold events are explicitly informational until a side-effect handler is registered; unknown event types fail normally. Event IDs and the in-process dispatcher provide handler deduplication within a process, while all production side-effect handlers must additionally be durably idempotent because crash recovery remains at-least-once.

`make outbox-process` runs one bounded batch and prints machine-readable counts. A scheduler or process supervisor must invoke it continuously in deployed environments.

## Consequences

A committed mutation can no longer be reported as failed because a downstream handler failed. Multiple PostgreSQL workers do not hold the same active claim concurrently, active async handlers renew their leases, stale claims recover after crashes, and poison events cannot loop forever. External brokers remain replaceable future adapters rather than a requirement for this gate.
