# Changelog

## [Unreleased]

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
