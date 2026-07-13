# Phase 2B.1 training-plan checkpoint

Date: 2026-07-13  
Branch: `codex/fix-scaffold-reproducibility`  
Implementation commit: `5f6fc73c342445141fe76eccb4ec70b837b99ccb`

## Completed

- Gate UI.1 is complete at `62945a79572854e5430b9b70019e070a559b6ebf`.
- ADR-017 defines the local training-plan aggregate, typed custom/catalog exercise references,
  durable snapshots, calendar boundary and future sync boundary.
- Room is version 7 with committed schemas 1–7 and an explicit 6→7 migration.
- Owner-scoped plans support normalized weeks, relative days, blocks, exercises and complete set
  prescriptions; aggregate replacement, copy, activation, archive and delete are transactional.
- Two self-authored CC0 offline starters seed idempotently and do not reappear after deletion.
- The Room-backed UI provides list/detail/editing, structure and set reordering, visibly separated
  public/private exercise choices, active-location compatibility and confirmed adapt-as-copy.
- `WorkoutPlanSnapshot` freezes plan/day identity, revision, exercise/reference/set data, planned
  duration, optional location/start instant and IANA time zone. Workout execution was not started.

## Verification evidence

- Backend: Ruff format/check and mypy passed; 128 PostgreSQL tests passed with zero skips and
  79.37% combined coverage.
- Alembic: fresh PostgreSQL and SQLite upgrade/check passed at sole head `0d4f6a8b2c17`.
- Android: `spotlessCheck detekt test lintDebug assembleDebug` passed, 656 actionable tasks;
  60 distinct JVM tests / 112 executions, zero failures, errors or skips.
- AndroidTest compile gate passed, 208 actionable tasks. Connected execution was not claimed.
- Lint: zero errors; 28 documented dependency/toolchain warnings.
- OpenAPI drift, Compose config/build/up, container migration, smoke, repository check, Gitleaks,
  Trivy fixed-vulnerability gates and APK inspection passed. Unfixed runtime-image findings remain
  reported by CI rather than silently ignored.
- Debug APK SHA-256: `0656dd88dc9cc90ec56c2ec149f545355a62243e00b39b991a955935899344c6`.
- Source archive: 286 files, SHA-256
  `e918369506a545ed172274f0bf4762b06285a394a0d6a8092631ed8107f375e3`.
- CI: [run 29290254292](https://github.com/Saltybukket/Momentum/actions/runs/29290254292),
  attempt 1, exact implementation head `5f6fc73c342445141fe76eccb4ec70b837b99ccb`; Android,
  backend and repository-security jobs passed without reruns.

## Connected-test blocker

The stable Windows API-36 Google APIs x86_64 system image/AVD remains unavailable. This blocks
only runtime instrumentation/visual/accessibility acceptance. AndroidTest sources compile.

## Exact next gate

Begin Phase 2B.2 only: accept the calendar ADR, implement Room 7→8 and the five specified schedule,
rule, occurrence, availability and override tables, then complete materialization, conflict logic,
Today/Week/Month/Agenda UI, migrations and the full matrix. Do not begin workout execution,
gamification, sync, privacy expansion or unrelated UI work.
