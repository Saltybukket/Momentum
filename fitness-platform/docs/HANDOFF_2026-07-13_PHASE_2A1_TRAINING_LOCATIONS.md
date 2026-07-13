# Phase 2A.1 training locations handoff

## Checkpoint

- Date: 2026-07-13
- Branch: `codex/fix-scaffold-reproducibility`
- Implementation commit: `745894aad1155a6a44f3367574a78810b9f287de`
- Commit subject: `feat(android): add training locations and equipment-aware catalog`
- Push: `origin/codex/fix-scaffold-reproducibility` contains the implementation commit.
- Implementation CI: [run 29256173819](https://github.com/Saltybukket/Momentum/actions/runs/29256173819), attempt 1, success, head SHA `745894aad1155a6a44f3367574a78810b9f287de`.
- CI jobs: backend, Android and repository-security all passed. No rerun or cancellation occurred.

Gate Q remains the prerequisite checkpoint at `ed34e4f861f63ac1dee081fb0710823748d9ab71`;
[run 29253072791](https://github.com/Saltybukket/Momentum/actions/runs/29253072791)
passed that exact commit. The patched pytest line is retained and verified as pytest 9.1.1 with
pytest-asyncio 1.4.0 and pytest-cov 6.3.0.

## Delivered local contract

- `TrainingLocation` has a local UUID, normalized bounded name, `LocationType`, active state,
  timestamps, revision and soft-delete tombstone.
- The stable registry contains 33 equipment definitions. `none` is implicit, always available and
  non-physical; `open-floor` is explicit inventory.
- Home basic, Gym full, Outdoor minimal and Empty custom presets remain editable suggestions and
  are persisted only after confirmation.
- Room version 6 adds `training_locations` and `training_location_equipment`. The nullable unique
  active slot permits at most one active non-deleted location. Active switching, equipment
  replacement and replacement activation after deletion are atomic.
- Migration 5→6 preserves profile, custom-exercise, workout and catalog rows; schema 6 is exported.
- Home and Profile expose location management. The screen supports list, create, rename/type edit,
  delete confirmation, activation, categorized searchable localized equipment selection,
  selected count and Save/Cancel semantics.
- Compatible catalog mode requires an active location and never silently grants the full catalog.
  The explicit all-exercises mode reports missing equipment. Alternatives share primary muscle,
  prefer tracking type and are deterministically ordered by name and ID.

This is a local-first Android slice. It does not add backend location tables, APIs, outbox
operations, pull cursors or sync claims. The UUID/revision/tombstone model leaves a future sync
boundary possible without pretending that it already exists.

## Verification

Backend and migrations:

- Ruff format/check and mypy passed (`43` typed source files).
- Dedicated real PostgreSQL suite: `128 passed`, `0 skipped`, pytest 9.1.1, `79.27%` combined
  statement/branch coverage.
- Fresh SQLite and dedicated PostgreSQL `alembic upgrade head` plus `alembic check` passed.
- Exactly one Alembic head: `0d4f6a8b2c17`.

Android:

- `./gradlew spotlessCheck detekt test lintDebug assembleDebug --no-daemon`: passed, 579 tasks.
- 43 distinct JVM tests passed as 81 debug/release/test task executions; no skips or failures.
- All required AndroidTest compilation targets passed, 192 tasks: database, datastore, sync, data
  and app.
- Room/data instrumentation sources cover migration preservation, CRUD, active switching,
  equipment replacement, active deletion, duplicate relations, foreign-key cascade and repository
  behavior. They compiled but were not executed without a device.
- Debug APK: 40,585,190 bytes; SHA-256
  `fa17cc9964a21df59eeb82cb477e1ead483f9c87834a3304be53dd10f2a68c59`.

Platform and security:

- `make doctor` passed with only the documented stable Windows API-36 AVD warning.
- OpenAPI export had no drift; Docker Compose config/build/up and health checks passed.
- The backend container migrated to `0d4f6a8b2c17`; `make smoke` passed.
- Repository integrity and `git diff --check` passed.
- Gitleaks 8.30.0 scanned 46 commits and found no leaks.
- Trivy 0.66.0 filesystem and built runtime-image policy scans found zero fixed
  HIGH/CRITICAL vulnerabilities. CI also preserved the all-findings reports.
- Implementation source archive: 252 tracked files; SHA-256
  `147d6565ed17d12272c1d91b9ab265810c6f5bf20f93b6a8c34d2bdfb8871728`.

The non-interactive shell did not initially include `$HOME/.local/bin`, so the first local
`make openapi` and `make smoke` invocations could not resolve `uv`. Both commands were rerun with
the canonical local `uv` path and passed. This was an invocation-environment issue, not a product
failure.

## Remaining external blocker and next scope

Connected/UI acceptance is not claimed because no stable Windows API-36 Google APIs x86_64 AVD is
available. Install the image with:

```text
sdkmanager.bat "system-images;android-36;google_apis;x86_64"
avdmanager.bat create avd -n Momentum_API_36 -k "system-images;android-36;google_apis;x86_64"
```

Phase 2A.2 location sync was deliberately not started. The next product slice named by the active
task is Phase 2B: `ExerciseReference` plus editable training plans with days, blocks, ordered
exercises, sets/reps/RPE/RIR/rest/tempo. Re-read the then-current task, `AGENTS.md`, `STATUS.md` and
accepted ADRs before opening that domain.
