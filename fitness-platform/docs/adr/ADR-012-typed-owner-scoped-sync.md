# ADR-012: Typed, owner-scoped sync commands

- Status: Accepted
- Date: 2026-07-12

## Context

The original sync endpoint accepted free-form dictionaries and duplicated CRUD rules. Invalid UUIDs/enums could escape as `500`, oversized fields bypassed CRUD limits, workout links could reference another owner's private exercise, and `operation_id` had no server-side meaning across batches.

## Decision

The transport contract is a closed union of Profile UPSERT, Exercise UPSERT, Exercise DELETE and Workout UPSERT operations. Each payload forbids extra fields and validates required UUIDs, enums, lengths, unique link IDs and unsafe control/bidirectional characters before reaching application code. Application code receives typed domain commands rather than transport dictionaries.

Private exercise and workout identifiers are owner-scoped. A foreign, deleted, public-catalog or missing exercise is reported only as an unavailable reference; sync does not reveal its owner. Global UUID collisions are controlled conflicts.

Every operation is deduplicated by `(owner_user_id, operation_id)` in the same transaction as its domain mutation. The versioned canonical request hash covers `contract_version`, `entity_type`, `action` and the typed payload using sorted compact UTF-8 JSON. It permits deterministic replay only for the same semantic command and rejects reuse with changed content, entity or action. Records expire after seven days. An expired reservation is atomically replaced while locked during `reserve()`; `make sync-operation-cleanup` remains bounded operational maintenance rather than a correctness prerequisite.

## Consequences

Malformed input is a controlled `422`, payload mismatch is `409`, and retries across distinct HTTP idempotency batches do not repeat domain writes. PostgreSQL is the concurrency authority; SQLite runs with foreign keys enabled for parity. Workout lifecycle commands remain governed by the separate workout-state ADR introduced in Gate C.
