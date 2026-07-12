# ADR-013: Workout lifecycle and private exercise links

- Status: Accepted
- Date: 2026-07-12

## Context

General workout updates and free-form sync previously allowed arbitrary status assignment, including completion without timestamps or events and transition back to `PLANNED`. Replacing link collections without an intervening flush could also violate the unique `(workout_id, position)` constraint.

## Decision

The supported lifecycle is `PLANNED -> IN_PROGRESS -> COMPLETED`. `start` and `complete` are explicit commands shared by HTTP CRUD and sync. Completion requires a started workout, records `end_time`, persists exactly one deterministic `WorkoutCompleted` outbox event, and is idempotent. `COMPLETED` is terminal and its workout cannot be generally edited or restarted. Cancellation is outside the current narrow slice and is not exposed.

General create/update commands contain title, notes and at most 50 unique owner-scoped active private exercise IDs; they never accept status. Public catalog IDs, foreign IDs, deleted IDs and missing IDs share the same unavailable-reference response. Validation loads the owned ID set in one query.

Link replacement clears and flushes existing relationship rows before inserting the new ordered set. Empty, add, remove, reorder and unchanged updates therefore share one deterministic path without transient uniqueness violations.

## Consequences

Status changes always pass through lifecycle commands and durable outbox events. Sync uses `UPSERT`, `START` and `COMPLETE` operations rather than copying client status. A future immutable workout-session snapshot can extend the completion command without reopening general status mutation.
