# Handoff: Phase 2B.2.1 Calendar Integrity Complete

Date: 2026-07-15

Branch: `codex/fix-scaffold-reproducibility`

Repository: `/home/student/projects/Momentum` in WSL2 `ISP2025`

## Completion boundary

Phase 2B.2.1 is complete. Do not reopen calendar integrity work from older handoffs. The next new
gate requires a separate user instruction. Workout Execution, calendar sync, gamification and the
visual design sprint were not started by this resume task.

## Implementation checkpoint

The final calendar-integrity implementation is commit
`9550f9ccc2aa8f9ba58ce165307fa48633d50c1d` (`fix(android): make calendar lifecycle transactions
atomic`). It is pushed on `codex/fix-scaffold-reproducibility`.

Exact implementation CI:

- run `29347954640`, attempt 1;
- head `9550f9ccc2aa8f9ba58ce165307fa48633d50c1d`;
- backend, Android and repository-security jobs passed;
- <https://github.com/Saltybukket/Momentum/actions/runs/29347954640>.

## Closed resume findings

- Schedule creation plus first materialization and confirmed rule replacement plus
  rematerialization each use one Room transaction, with injected-failure rollback tests.
- `START_NEW_SCHEDULE_SETUP` preserves the active plan/schedule until preview confirmation. The
  ViewModel retains a resumable setup state and the data coordinator performs the final
  plan/schedule switch atomically.
- Archive/delete and schedule deactivation are coordinated atomically.
- Conflict queries load the visible civil-date range with a one-day margin on each side and expose
  only visible occurrences/conflicts; day, week, month and cross-midnight boundaries are covered.
- Migration 8→9 classifies a copy of a moved occurrence as `COPIED`; Room remains version 9.
- Removing a plan day referenced by calendar state is deliberately unsupported and fails safely in
  this gate. There is no silent cascade and no overclaim of a completed destructive flow.

## Local verification

Backend and platform:

```text
Ruff format/check: passed
mypy: passed (43 source files)
PostgreSQL pytest: 129 passed, 0 skipped, 79.20% combined statement/branch coverage
Alembic head: 0d4f6a8b2c17
Fresh SQLite upgrade/check: passed
Separate PostgreSQL upgrade/check/current: passed
OpenAPI export/drift: passed
Docker Compose config/build/up/health: passed
Container migration and smoke flow: passed
Repository check and git diff --check: passed
Gitleaks: no secret finding
Trivy fixed HIGH/CRITICAL findings: 0 filesystem, 0 runtime image
Trivy all-finding report: 0 filesystem and 20 unfixed HIGH/CRITICAL runtime-image findings
```

Android:

```text
spotlessCheck, detekt, test, lintDebug, assembleDebug: passed
changed AndroidTest source modules: compiled
combined final Gradle invocation: 701 actionable tasks, 120 executed, 581 up-to-date
Android JVM reports: 78 distinct tests, 134 task/variant executions
Room version: 9; exported schemas 1–9
```

Connected Android tests were not executed because the stable Windows API-36 system image/AVD is
still unavailable. Compilation is not presented as connected execution.

## Canonical state

- Alembic head: `0d4f6a8b2c17`.
- Android Room version: 9; schemas 1–9 are committed.
- Public OpenAPI did not change.
- `icon_and_image_ideas` is not an Android source set and package inspection found no such APK
  entry.
- The final documentation commit, its exact CI run and its twice-generated source archive are
  reported in the completion response because those facts can only exist after this file is
  committed.

## Remaining external blocker and later scope

The missing stable Windows API-36 Google APIs x86_64 system image/AVD blocks only connected/UI
acceptance execution. Install it with:

```text
sdkmanager.bat "system-images;android-36;google_apis;x86_64"
avdmanager.bat create avd -n Momentum_API_36 -k "system-images;android-36;google_apis;x86_64"
```

Workout Execution must separately harden the execution snapshot boundary, including authoritative
time-zone validation. Sync, gamification and premium visual redesign remain later gates.
