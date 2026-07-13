# ADR-018: Local training calendar and bounded occurrence materialization

- Status: Accepted
- Date: 2026-07-13

## Context

Relative plan days need concrete local calendar instances without mutating their reusable plan.
Users need multiple sessions per day, optional local start times, locations, availability and
one-off exceptions. Local civil time must remain understandable across daylight-saving changes.

## Decision

`PlanSchedule` owns recurring `PlanDayScheduleRule` rows and references an immutable plan/day
identity. A schedule stores an IANA time-zone ID. Rules use a weekday, optional local time, duration
and optional location. Activating or editing a schedule materializes only a documented rolling
eight-week horizon in one Room transaction.

`ScheduledWorkoutOccurrence` is a separate dated aggregate. It stores local date/time plus the
schedule time zone and title/duration/location snapshots. Multiple rows per date are valid. Moving
one occurrence changes only that row and records its original date. A permanent change edits the
rule; future rematerialization requires explicit confirmation and never changes `IN_PROGRESS` or
`COMPLETED` rows.

Availability rules recur by weekday; overrides are one-date exceptions. Calendar conflicts are
derived domain values rather than persisted authority. Skip and cancel are terminal planning
states. Copy creates a new planned occurrence. No cloud/external-calendar sync is introduced.

## Consequences

- Room schema 8 adds schedule, rule, occurrence, availability and override tables.
- At most one active schedule exists per owner through a nullable unique active-slot key.
- Local date/time and IANA zone identity survive DST changes without silently shifting civil time.
- Plan editing, occurrence editing and future workout history remain separate boundaries.
