# Phase 2B.2 flexible training-calendar checkpoint

Date: 2026-07-13

Branch: `codex/fix-scaffold-reproducibility`

Implementation commit: `376659cd679ff81971512b8c5d1802df012292c3`

## Completed

- ADR-018 defines the Android-local schedule, occurrence, availability, override and conflict
  boundaries. Workout execution, calendar synchronization and gamification were not opened.
- Room is version 8 with committed schemas 1–8 and an explicit 7→8 migration. The migration
  preserves existing plans and canonicalizes every snapshot equipment requirement as a sorted JSON
  array rather than retaining only one slug.
- Owner-scoped schedules support multiple plan sessions per day, deterministic eight-week
  materialization, IANA-zone validation, DST-safe civil dates/times, one-off changes and explicitly
  confirmed future replacement while preserving terminal/history rows.
- Availability, maximum duration, location/equipment, unavailable exercise and overlap conflicts
  are derived from Room source-of-truth data. Full equipment sets participate in compatibility.
- Adapt-as-copy is a single transactional domain/repository operation. Plan descriptions are
  preserved, structure/default-set/adaptation mutations live in domain use cases, and exercise-set
  ordering is implemented rather than merely claimed.
- Plan, exercise, set, availability and occurrence editors retain input and close only after
  persistence succeeds. Equipment, resolution and set-type values are rendered through localized
  labels rather than raw slugs/enums.
- The Workouts root now exposes Today, Calendar, Plans and History, with Today/Week/Month/Agenda
  calendar views, date navigation, schedule/occurrence editing, availability editing and explicit
  loading, empty, error and accessibility semantics.

## Verification evidence

- `make doctor`: passed all core prerequisites; only the documented stable Windows API-36 AVD
  warning remains.
- Backend: `uv sync --extra dev --frozen`, Ruff format/check and mypy passed. `make test` used the
  real dedicated PostgreSQL test database: 128 passed, zero skipped, 79.37% combined coverage.
- Alembic: fresh PostgreSQL and fresh SQLite upgrade/check passed; sole head
  `0d4f6a8b2c17`. The running backend container reports the same current revision.
- OpenAPI export matched `shared/openapi.json`; no backend calendar endpoint was introduced.
- Android: `spotlessCheck detekt test lintDebug assembleDebug --no-daemon` passed with 656
  actionable tasks. Results contain 65 distinct JVM tests / 118 debug, release and pure-JVM
  executions, with zero failures, errors or skips. `make lint` and `make android-build` also passed.
- AndroidTest compile gate passed with 208 actionable tasks for database, datastore, sync, data and
  app. This compiles the Room 7→8 migration/DAO/repository tests; connected execution is not claimed.
- Docker Compose config/build/up, container migration and smoke passed. The first smoke attempt hit
  the newly recreated backend during `health: starting`; after health became green, the complete
  smoke flow passed.
- Repository integrity and `git diff --check` passed. Gitleaks scanned about 72.43 MB with no leak.
  Trivy reported zero fixed HIGH/CRITICAL filesystem or runtime-image findings; the full image
  report retains 20 currently unfixed HIGH/CRITICAL Debian findings.
- Android lint has zero errors and only the existing 28 dependency/toolchain availability warnings.
  `icon_and_image_ideas/` is absent from the APK.
- Debug APK SHA-256:
  `6990e40ac67fb6ed0e4c102769855c5427c0dea771a0696de97c8bf4519c33c1`.
- Source archive: 300 files, SHA-256
  `5a698a85b94d7bd825c09b2716a3184ba637a61a00d181fa8b0e54bbfc4bcf05`.
- Implementation CI: [run 29294493621](https://github.com/Saltybukket/Momentum/actions/runs/29294493621),
  attempt 1, exact head `376659cd679ff81971512b8c5d1802df012292c3`; Android, backend and
  repository-security jobs passed without reruns.

## Remaining external blocker

The stable Windows API-36 Google APIs x86_64 system image/AVD remains unavailable. This blocks only
connected Room migration, visual and accessibility acceptance execution. All AndroidTest sources
compile. Install it on Windows with:

```text
sdkmanager.bat "system-images;android-36;google_apis;x86_64"
avdmanager.bat create avd -n Momentum_API_36 -k "system-images;android-36;google_apis;x86_64"
```

GitHub Actions also emits a non-failing maintenance warning because several upstream actions still
declare Node.js 20 and are temporarily forced onto Node.js 24.

## Next gate

No later feature gate is open. Use the next accepted product task and ADR before beginning workout
execution, sync, privacy expansion, gamification or another domain.
