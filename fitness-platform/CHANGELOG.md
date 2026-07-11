# Changelog

## Unreleased

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

## [Unreleased]

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
