# ADR-019: Calendar integrity and occurrence snapshot lifecycle

- Status: Accepted
- Date: 2026-07-14
- Supersedes: the complete child replacement statement in ADR-017

## Context

Room 8 made plan days cross-aggregate references of schedule rules and occurrences. The existing
plan update transaction deleted and reinserted the complete child tree. SQLite therefore cascaded
rule deletion and nulled occurrence references even when a user changed only plan metadata. The
same calendar prototype did not distinguish generated, moved, copied and ad-hoc occurrences,
ignored multiweek plan cycles and had no rolling horizon maintenance contract.

Workout execution must not consume this unstable planning state. The local calendar needs durable
logical snapshots and explicit destructive decisions before that later gate can begin.

## Decision

### Plan aggregate updates and removal

Normal plan updates are differential Room transactions. Existing week, day, block, exercise and
set IDs are updated in place; new IDs are inserted; ordering uses a collision-safe two-phase
position update. Metadata, set edits and reordering never delete unrelated children.

Removing a plan day or an ancestor that contains one is a separate domain decision. If any schedule
rule or retained occurrence refers to the affected logical day, an unconfirmed operation returns
`BLOCKED_PENDING_DECISION`. A confirmed operation first removes or detaches affected future
planning rows according to the displayed impact, preserves occurrence snapshots/history, and only
then removes template rows. Foreign-key cascade is not business workflow.

Archiving or soft-deleting a plan preserves all occurrences. An affected active schedule is
deactivated explicitly in the same transaction. Ordinary plan updates cannot delete schedule
rules.

### Room 9 and snapshot identity

Room 9 changes the schedule-rule → plan-day foreign key to `RESTRICT`. Occurrences retain nullable
live references for navigation and add immutable planning fields:

- `planDayIdSnapshot`;
- `planRevisionSnapshot`;
- `planWeekIndexSnapshot`;
- canonical `requiredEquipmentSnapshotJson`;
- `hasUnavailableExerciseSnapshot`.

Existing Room-8 rows are backfilled from their current plan tree where possible and retain the old
logical day ID as snapshot identity otherwise. Migration never discards schedules, rules,
occurrences, history, availability or overrides.

### Occurrence origin and one-off behavior

Every occurrence has one origin: `GENERATED`, `MOVED_ONCE`, `COPIED` or `AD_HOC`. A moved occurrence
keeps `PLANNED` status, records its source slot and is a detached override. Its deterministic
generated identity suppresses recreation of the original slot. A copy is detached and records
`sourceOccurrenceId`, but is never represented as a move. Permanent rule replacement affects only
unchanged `GENERATED` future rows after an explicit impact preview and confirmation.

### Cycle and rolling horizon

For a nonnegative date offset from `schedule.startDate`, the active plan week is:

```text
floor(daysBetween(startDate, date) / 7) mod planWeekCount
```

Only rules whose plan day belongs to that plan week materialize. Schedule setup proposes defaults
but persists nothing until confirmation.

`EnsureCalendarHorizonUseCase` idempotently ensures generated occurrences from today through
today + 55 days. It runs on calendar open and schedule activation. It fills gaps without rewriting
history, overrides, moved/copy/ad-hoc rows or existing generated rows. A background worker is not
introduced because calendar-open and activation hooks provide a deterministic local contract
without adding scheduling infrastructure.

### Active plan and schedule

The active schedule is the planning authority for its own `planId`; the globally active plan is an
editing/default-selection preference. Activating a different plan requires an explicit UI choice:
keep the current schedule, create/activate a schedule for the new plan, or cancel. Conflict
detection never substitutes the global active plan for an occurrence snapshot. Archiving/deleting
the scheduled plan deactivates its schedule while preserving history.

### Conflict intervals and DST

Conflict detection uses occurrence-specific snapshots and complete equipment sets. Equipment with
no selected location yields `LOCATION_REQUIRED`. Intervals are built from local date/time and IANA
zone. A nonexistent DST-gap local time is invalid and yields a planning conflict; a duplicated
fall-back time deterministically chooses the earlier offset. Durations may cross midnight and all
members of every overlap are marked using full interval intersection, not adjacent pairs.

### Commit-reproducible source archives

Source archives read path, bytes and Unix mode from one explicit Git commit tree. Their manifest
contains repository identity, full commit SHA, file count and generator version. Working-tree
content, mtimes, local execute-bit changes and build outputs cannot change the archive for that
commit. The archive hash is evidence outside the archived commit and is never self-referential.

## Consequences

- Room version 9 and schema 8→9 migration are required.
- Plan persistence becomes more complex but cross-aggregate identities survive ordinary edits.
- Destructive plan edits and plan/schedule activation require explicit impact decisions.
- Calendar materialization and conflicts become deterministic inputs for the later immutable
  workout-execution snapshot; workout execution itself remains out of scope.
- ADR-017 remains authoritative except for complete child-tree replacement, which this ADR
  replaces with differential persistence.
