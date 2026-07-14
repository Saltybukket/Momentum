# Changelog

## [Unreleased]

### Phase 2B offline training calendar

- Added Room schema 8 with owner-scoped plan schedules, recurring plan-day rules, dated workout
  occurrences, availability rules and one-date overrides, plus an explicit 7→8 migration.
- Added Room schema 9 snapshot/origin integrity and corrected 8→9 migration precedence so copies
  of moved occurrences remain `COPIED`.
- Added deterministic rolling 56-day civil-time materialization, one-off moves/copies, explicitly
  confirmed future-rule replacement, terminal-history protection and derived conflicts.
- Made schedule creation/materialization, confirmed rule replacement, plan/schedule activation and
  archive/delete lifecycle changes atomic, with injected-failure rollback coverage.
- Kept the old plan and schedule active throughout resumable replacement setup, expanded conflict
  queries across visible-range boundaries and explicitly blocked referenced plan-day removal
  instead of permitting a silent cascade.
- Added Today/Calendar/Plans/History navigation, Week/Month/Agenda views and
  persistence-confirmed schedule/occurrence editors.
- Corrected Phase 2B.1 by preserving all equipment requirements, making adapt-as-copy atomic,
  retaining descriptions, moving mutations into domain use cases, localizing labels and adding set
  reordering.

### Premium UI and UX checkpoint

- Completed explicit Material 3 light/dark roles and contrast tests, production-screen previews,
  root-versus-subpage Up navigation, scroll-safe critical screens, API-28 splash branding and an
  adaptive-icon safe-zone adjustment.
- Added a fail-closed Android startup gate: Room must open before feature UI or private sync is
  available, while a data-preserving retry state handles startup failure.
- Added a dedicated Android design-system module with calm navy/silver/amber light and dark themes,
  centralized type, spacing, shape, elevation and motion tokens, responsive width helpers and
  reusable card, empty-state and skeleton components.
- Added a self-authored adaptive geometric-M launcher mark, monochrome themed icon, consistent
  splash surface and explicit no-backup/data-extraction rules.
- Reworked the four-root shell and existing Home, Workouts, Exercises, Catalog, Training Locations,
  Profile, Privacy and Conflict surfaces without inventing unsupported product metrics or domains.
- Hardened Room downgrade startup, training-location text and finite-operation behavior, catalog
  compatibility and equipment labels before the visual pass.

### Phase 2A.1 training locations

- Added a local-first training-location domain with stable equipment definitions, editable
  inventories, presets and an explicit single-active-location invariant.
- Added Room schema 6, a data-preserving 5→6 migration, transactional active switching,
  equipment replacement and deterministic replacement activation after soft deletion.
- Added Home/Profile location management and equipment-aware catalog filtering, missing-equipment
  explanations and deterministic compatible alternatives. Location synchronization is not yet
  implemented or claimed.
- Added domain, ViewModel, Room migration and repository coverage; connected execution remains
  dependent on a stable Windows API-36 AVD.

### Gate Q quality hardening

- Added structured catalog-import error codes and field paths so validation tests assert controlled
  rejection and no-write behavior independently of optional JSON Schema format-check ordering.
- Upgraded the locked backend test runner to the patched pytest 9 line.
- Replaced invisible root-navigation placeholders with self-authored vector icons, mapped nested
  routes to stable root families and moved remaining catalog labels and semantics into resources.

### Momentum App Shell V1

- Replaced scaffold-facing navigation with a resource-backed four-root Momentum shell for Home,
  Workouts, Exercises and Profile.
- Added Material 3 light/dark branding, compact bottom navigation and an expanded navigation rail.
- Added a local-first dashboard for active and recent workouts, catalog/custom-exercise shortcuts,
  conflicts, pending/blocked sync state, offline readiness and privacy recovery access.
- Added deterministic ViewModel and navigation tests for empty/local-data, conflict, sync and
  adaptive-layout states without introducing placeholder product domains.

### Gate F.1 catalog verification and Gate E.2 Android recovery

- Unified backend/API/Android catalog canonicalization so semantic array order cannot change a
  release hash, and added an exact shared API fixture accepted by Android verification.
- Preserved the active catalog snapshot through downgrade and re-upgrade, including empty and
  non-activated databases, and hardened catalog text and absolute-HTTPS validation.
- Added an explicit confirmed guest-credential replacement flow that preserves local data and
  only re-enqueues opted-in sync after successful reset.
- Bound Android outbox success, failure and conflict finalization to the active claim owner and
  added stale-worker regression coverage.
- Compile every Android instrumentation-test source set in CI without claiming connected
  execution.

### Gate H-lite project authority and container delivery

- Established `docs/STATUS.md` as the concise current authority and archived superseded reports.
- Added a reproducible allowlisted source archive with safe paths and SHA-256 output.
- Split filesystem and loaded runtime-image vulnerability scans while retaining unfixed reports.
- Changed the backend image to a digest-pinned multi-stage non-root runtime without tests or dev
  dependencies.

### Gate F immutable catalog releases

- Stage complete catalog content in immutable release-scoped tables and atomically select one
  active release, with retained history and explicit rollback.
- Reject conflicting version/hash/batch identities, duplicate relations, unsafe license URIs and
  invalid publication/review state before public activation.
- Bind public pages and snapshots to one release identity and content hash, with stable ETag and
  literal wildcard search semantics.
- Validate complete Android snapshots and canonical SHA-256 before atomically replacing the Room
  cache; preserve the prior cache on rejection.
- Advance the Alembic head to `0d4f6a8b2c17`.

### Phase 2B.1 editable offline training plans

- Added typed custom/catalog exercise references with durable snapshots and explicit unavailable,
  deprecated and deleted-private resolution states.
- Added Room schema 7 for owner-scoped plan/week/day/block/exercise/set aggregates, atomic copy and
  reorder, soft delete, archive/restore and one active plan per profile.
- Added idempotent self-authored CC0 starter plans plus offline plan list, detail, editor, exercise
  picker, set editor, location compatibility and adapt-as-copy UI.
- Defined and tested the immutable `WorkoutPlanSnapshot` boundary without beginning workout
  execution or plan synchronization.

### Gate R/D reliability hardening

- Bound sync replay identity to contract version, entity, action and canonical typed payload; expired operation IDs are atomically reusable.
- Made `CANCELLED` workouts terminal and unified CRUD/sync text-control validation.
- Execute PostgreSQL regression tests in CI against a database separate from Alembic.
- Made HTTP success depend only on durable commit and added a leased database-outbox processor with retry, backoff and dead letters.
- Bounded request IDs and standardized redacted unexpected-error responses.
- Advanced the Alembic head to `e15b7c9d420f`.

### Gate D.1/E Android security

- Hardened outbox ownership with unique claim tokens, just-in-time claims, async lease heartbeats and explicit lost-claim reporting.
- Advanced the Alembic head to `f26c8d0e531a`.
- Added Room schema 5 with an atomic private-exercise pull cursor and safe full replay after schema-4 upgrades.
- Protected guest bearer/recovery credentials with Android Keystore AES/GCM and repeatable plaintext migration.
- Made private-sync consent enqueue or cancel unique work and added worker consent checks at private network boundaries.
- Added recoverable Catalog seed/refresh/detail and Privacy consent states with visible strings in resources.
- Separated bearer renewal from recovery-proof reset, blocked rejected/corrupt recovery safely, released owner-scoped Android outbox claims on consent cancellation and made catalog detail failures reachable.

### Exercise conflict synchronization

- Added persisted private-exercise conflict records with local and remote snapshots, explicit
  keep-local, take-server and manual-merge resolution paths, and confirmation after push success.
- Extended sync conflicts to return the server exercise snapshot so the Android client never
  resolves a conflict without both comparison versions.
- Added Room database version 2 and exported schema for conflict persistence.
- Added a focused connected-test aggregation task and a Windows-emulator/WSL ADB runner that
  bypasses UTP's external-emulator console limitation.
- Added atomic Room repository instrumentation coverage for keep-local, take-server and manual
  exercise-conflict resolution, plus a `make android-connected-test` workflow with explicit
  device selection and bounded runner timeouts.

All notable scaffold changes are documented here. The project follows semantic versioning once public releases begin.

### Scaffold reproducibility

### Fixed

- Made the Linux Gradle bootstrap executable and checksum large distributions without loading the archive into the 64 MB wrapper heap.
- Aligned Hilt, AndroidX Hilt and Lifecycle versions with the AGP 8.13/API 36 baseline.
- Fixed Android module classpaths, Compose test BOM resolution, Detekt source selection and Compose compilation issues exposed by the first full build.
- Exported the initial Room schema and verified Android unit tests, formatting, Detekt, lint and debug assembly in WSL.
- Re-locked backend dependencies against public PyPI instead of an environment-specific internal package mirror.
- Made repository integrity checks ignore local generated output while still rejecting tracked or non-ignored generated files.

## [0.1.0-scaffold] - 2026-07-10

### Added

- Monorepository architecture and development tooling.
- FastAPI modular-monolith backend, PostgreSQL/Alembic schema and Redis foundation.
- Development guest identity, profile, private custom exercise and minimal workout APIs.
- Request IDs, structured errors, idempotency and internal event/outbox infrastructure.
- Android offline-first Compose/Room/DataStore/WorkManager/Hilt scaffold.
- Local guest profile, custom exercise CRUD and minimal workout lifecycle.
- Deterministic integration mocks and provider ports.
- Backend, Android and E2E test foundations.
- Docker Compose, CI, documentation, ADRs, demo data and implementation roadmap.

### Explicitly not complete

Production authentication, Health Connect/vendor adapters, full workout/planning/nutrition/analytics, gamification, social/groups/bosses, commerce/ads, AI, Play Integrity and administration.

### Offline exercise catalog foundation

- Added validated atomic catalog import, CC0 demo data, machine-readable report and public filter API.
- Added Room 3 catalog storage, atomic refresh, offline seed, list/detail/filter UI and accessibility semantics.
- Made guest synchronization opt-in by default and moved executable CI to the repository root.
