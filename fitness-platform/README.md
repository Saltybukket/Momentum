# Fitness Platform — Architecture Scaffold

A reproducible monorepository foundation for a future offline-first Android fitness, nutrition and gamification platform. This repository intentionally implements only the architecture assignment and one small vertical slice: local guest profile, private custom exercises, minimal workout lifecycle and idempotent backend synchronization.

> **Status:** architecture scaffold, not a complete fitness application. XP, quests, streaks, social, production health integrations, commerce, ads, AI, Play Integrity and the admin portal are extension contracts only.

## What is implemented

- Android 9+ client scaffold: Kotlin, Compose, Material 3, MVVM/UDF, Room, DataStore, WorkManager, Retrofit, Hilt.
- Offline guest profile persisted locally and editable.
- Local custom-exercise CRUD with validation and soft deletion.
- Minimal workout create/start/complete flow; local `WorkoutCompleted` domain event.
- Room outbox with retry/error/sync states and a WorkManager sync worker.
- FastAPI modular-monolith backend with clean domain/application/infrastructure/presentation separation.
- Development guest tokens, profile/exercise/workout endpoints, push sync, request IDs, structured errors and idempotency.
- PostgreSQL schema and Alembic migration; Redis health and future distributed rate-limit foundation.
- Deterministic provider mocks, module-boundary catalog and event dispatcher.
- Backend tests, Android unit/instrumentation/UI test foundations, CI and Docker Compose.
- Architecture, API, data model, security, privacy, integrations, testing, ADRs and phased implementation plan.

## Repository map

```text
android/          Android offline-first client
backend/          FastAPI modular monolith
admin/            Reserved audited administration delivery boundary
data/             Self-authored technical demo fixtures and license records
docs/             ADRs, architecture and implementation report
infrastructure/   Environment/deployment guidance
scripts/          OpenAPI export, PowerShell task runner and smoke test
shared/           Language-neutral/generated contracts
tests/e2e/        Cross-boundary E2E test strategy
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for the dependency rules and diagrams.

## Prerequisites

- Docker Engine with Docker Compose v2 for the full local backend stack.
- Python 3.12 or 3.13 and `uv` for host-based backend development.
- JDK 17 and Android SDK Platform 36 for Android builds.
- Android Studio with an emulator/device running Android 9 (API 28) or newer.
- GNU Make is optional; Windows users can use `scripts/dev.ps1`.

## Quick start: Docker backend

```bash
cp .env.example .env
docker compose up --build
```

Then open the health endpoint at `http://localhost:8000/health` and OpenAPI UI at `http://localhost:8000/docs` in local mode.

Seed clearly labelled demo data:

```bash
docker compose exec backend fitness-seed
```

## Host backend development

```bash
make setup
make migrate
make backend
```

Quality checks:

```bash
make backend-lint
make backend-test
make openapi
```

## Android

Open `android/` in Android Studio, install SDK Platform 36 and use JDK 17. The emulator connects to the local backend through `http://10.0.2.2:8000/`.

```bash
cd android
./gradlew test
./gradlew lintDebug assembleDebug
```

The included Gradle bootstrap downloads and verifies Gradle 8.13 on first use. The full Android unit-test, formatting, static-analysis, lint and debug-build command has been verified in WSL with Android SDK Platform 36; see `docs/IMPLEMENTATION_REPORT.md` for the exact command and remaining instrumentation limitation.

## Windows PowerShell

```powershell
Copy-Item .env.example .env
.\scripts\dev.ps1 setup
.\scripts\dev.ps1 dev
.\scripts\dev.ps1 backend-test
.\scripts\dev.ps1 android-build
```

## Demo flow

1. Start the Android app and create a local guest profile.
2. Add a private custom exercise.
3. Create a workout, start it and complete it.
4. Room writes each mutation and its outbox entry atomically.
5. WorkManager obtains a development guest token and pushes pending operations.
6. The backend upserts UUID-addressed entities and records idempotency/outbox data.

For a backend-only HTTP smoke run:

```bash
docker compose up --build -d
python scripts/smoke_test.py
```

## Test commands

```bash
make test             # backend + Android unit tests
make lint             # Python and Android static checks
make backend-test
make android-test
```

Actual executed results from this generation environment are in [docs/IMPLEMENTATION_REPORT.md](docs/IMPLEMENTATION_REPORT.md).

## Security and privacy warning

The temporary guest-token mechanism is a development foundation, not production authentication. Do not deploy it unchanged. Health data, purchases, rewards and social features are not implemented. See [SECURITY.md](SECURITY.md) and [PRIVACY.md](PRIVACY.md).

## Known limitations

- No production account linking, Google sign-in or email login.
- No Health Connect or vendor production adapters.
- Sync is push-only; pull, tombstone reconciliation and user-facing conflict resolution are planned.
- Sync acknowledgements mark local aggregate rows and outbox operations as `SYNCED`; pull sync, server deletions and conflict resolution are not yet implemented.
- No server-authoritative gamification, social, commerce, ads, AI or Play Integrity implementation.
- Demo exercise text is technical fixture data, not professional training guidance.

## Next steps

Follow [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md). The next three recommended vertical slices are also expressed as concrete Codex assignments in `docs/IMPLEMENTATION_REPORT.md`.
