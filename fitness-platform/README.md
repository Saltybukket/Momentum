# Momentum Fitness Platform

A reproducible monorepository for an offline-first Android fitness platform. The implemented scope
includes local guest/profile/workout foundations, private-exercise synchronization and an immutable
public exercise catalog; broader product domains remain planned.

> **Status:** working foundation, not a complete fitness application. See
> [`docs/STATUS.md`](docs/STATUS.md) for the canonical checkpoint and risks.

## What is implemented

- Android 9+ client scaffold: Kotlin, Compose, Material 3, MVVM/UDF, Room, DataStore, WorkManager, Retrofit, Hilt.
- Offline guest profile persisted locally and editable.
- Local custom-exercise CRUD with validation and soft deletion.
- Minimal workout create/start/complete flow; local `WorkoutCompleted` domain event.
- Room outbox with retry/error/sync states and a WorkManager sync worker.
- FastAPI modular-monolith backend with clean domain/application/infrastructure/presentation separation.
- Development guest recovery, profile/exercise/workout endpoints, private push/pull sync, request IDs, structured errors and idempotency.
- Immutable public catalog release import/activation, complete snapshot API and hash-verified Android Room refresh.
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

For a Windows emulator used from WSL, run `scripts/android-connected-tests.sh`. It builds and
installs test APKs with ADB directly, records raw output and intentionally targets only modules
with instrumentation sources. See [`android/README.md`](android/README.md).

The included Gradle bootstrap downloads and verifies Gradle 8.13 on first use. The full Android
unit-test, formatting, static-analysis, lint and debug-build command is verified in WSL with SDK
Platform 36; connected execution additionally needs the stable Windows API-36 AVD documented in
[`TESTING.md`](TESTING.md).

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

Current verification authority is [docs/STATUS.md](docs/STATUS.md) and [TESTING.md](TESTING.md).

## Security and privacy warning

The temporary guest-token mechanism is a development foundation, not production authentication. Do not deploy it unchanged. Health data, purchases, rewards and social features are not implemented. See [SECURITY.md](SECURITY.md) and [PRIVACY.md](PRIVACY.md).

## Known limitations

- No production account linking, Google sign-in or email login.
- No Health Connect or vendor production adapters.
- Private custom exercises use push-then-pull synchronization with revisions, a cursor feed and tombstones.
- Exercise conflicts preserve local and remote snapshots and require an explicit local/server/manual-merge choice; workouts and profiles remain push-only.
- No server-authoritative gamification, social, commerce, ads, AI or Play Integrity implementation.
- Demo exercise text is technical fixture data, not professional training guidance.

## Next steps

Follow [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md); current scope and the next bounded phase
are summarized in [docs/STATUS.md](docs/STATUS.md).

## Offline exercise catalog

Android ships three self-authored CC0 technical demo exercises and keeps the public catalog in Room as its source of truth. List, detail, muscle/equipment filters and offline/error states remain separate from user-owned custom exercises. `make catalog-import` validates and stages an immutable full release, atomically activates eligible newer content and emits a JSON report under `data/licenses/`; identical version/hash imports are true no-ops.

Public endpoints are `GET /api/v1/catalog/exercises`, `/api/v1/catalog/exercises/{id}`,
`/api/v1/catalog/snapshot`, `/api/v1/catalog/muscles` and `/api/v1/catalog/equipment`.
