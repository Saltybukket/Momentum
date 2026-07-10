# Changelog

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
