# ADR-017: Offline training-plan aggregate and exercise references

- Status: Accepted
- Date: 2026-07-13

## Context

Editable training plans must work without an account, network connection or backend contract. A
plan may refer to either a private custom exercise or an immutable public-catalog identity. Both
sources can later become unavailable, while an already authored plan must remain understandable.
The following calendar slice will schedule plan days, not duplicate plan content.

## Decision

`TrainingPlan` is the local owner-scoped aggregate. It owns ordered weeks, relative days, blocks,
exercise prescriptions and set prescriptions. The repository replaces and reorders the complete
owned tree in one Room transaction. One non-archived plan per owner may be active.

`ExerciseReference` is a typed union:

- `CUSTOM` stores a local custom-exercise UUID.
- `CATALOG` stores stable `source + externalId` and may cache the current catalog UUID.

Every reference stores an authored snapshot of display name, tracking type, equipment and primary
muscle. Resolution status is explicit (`RESOLVED`, `UNAVAILABLE`, `DEPRECATED`,
`DELETED_CUSTOM`); losing a source never destroys the snapshot or silently changes the plan.
Snapshots are presentation and execution provenance, not a second editable exercise catalog.

Plans are local-only in Phase 2B.1. UUIDs, timestamps, revisions and tombstones preserve a future
sync boundary, but no outbox operation or backend contract is introduced. A future sync design
must preserve owner scope, reference identity and atomic aggregate revisions.

Calendar schedules will reference `planId` and `planDayId`. They may create dated occurrences but
must not mutate the reusable template. Workout execution will consume an immutable
`WorkoutPlanSnapshot` captured from a resolved plan day. The snapshot also freezes planned
duration, optional training location, optional scheduled start instant and IANA time-zone ID;
execution is outside this gate.

## Consequences

- Room schema 7 adds six normalized plan tables with cascading ownership and unique positions.
- Aggregate copy, reorder, activation and archive operations are atomic.
- Public-catalog refreshes and custom-exercise deletion cannot make saved plans unreadable.
- Calendar and workout execution can depend on stable plan-day identities without owning plan
  authoring data.
