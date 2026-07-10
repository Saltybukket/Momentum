# ADR-005: Transactional local outbox synchronization

- Status: Accepted
- Date: 2026-07-10

## Context

A process can die between writing local data and scheduling a request. Direct “save then call API” logic can lose mutations or duplicate them during retry.

## Decision

Write each syncable local mutation and an outbox operation in one Room transaction. WorkManager transports pending operations with stable operation IDs, retry counters and explicit statuses. Backend writes are idempotent UUID upserts.

## Consequences

Local success is independent of connectivity and retries are durable. Pull synchronization/conflict resolution remain a later slice. Outbox payload versioning must be introduced before schemas diverge materially.
