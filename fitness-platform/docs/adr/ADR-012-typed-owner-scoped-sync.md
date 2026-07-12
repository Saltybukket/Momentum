# ADR-012: Typed, owner-scoped sync commands

- Status: Accepted
- Date: 2026-07-12

## Context

The original sync endpoint accepted free-form dictionaries and duplicated CRUD rules. Invalid UUIDs/enums could escape as `500`, oversized fields bypassed CRUD limits, workout links could reference another owner's private exercise, and `operation_id` had no server-side meaning across batches.

## Decision

The transport contract is a closed union of Profile UPSERT, Exercise UPSERT, Exercise DELETE and Workout UPSERT operations. Each payload forbids extra fields and validates required UUIDs, enums, lengths, unique link IDs and unsafe control/bidirectional characters before reaching application code. Application code receives typed domain commands rather than transport dictionaries.

Private exercise and workout identifiers are owner-scoped. A foreign, deleted, public-catalog or missing exercise is reported only as an unavailable reference; sync does not reveal its owner. Global UUID collisions are controlled conflicts.

Every operation is deduplicated by `(owner_user_id, operation_id)` in the same transaction as its domain mutation. A canonical payload hash permits deterministic replay of the stored result and rejects reuse with changed content. Records expire after seven days and `make sync-operation-cleanup` removes them in bounded batches.

## Consequences

Malformed input is a controlled `422`, payload mismatch is `409`, and retries across distinct HTTP idempotency batches do not repeat domain writes. PostgreSQL is the concurrency authority; SQLite runs with foreign keys enabled for parity. Workout lifecycle commands remain governed by the separate workout-state ADR introduced in Gate C.
