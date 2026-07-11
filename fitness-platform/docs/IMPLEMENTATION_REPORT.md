# Implementation Report

**Generated:** 2026-07-11
**Scope:** architecture assignment only  
**Repository version:** `0.1.0`

## 1. Scope interpretation

This repository implements the architecture and functional scaffold requested by the architecture document. The separate product master prompt was treated only as a compatibility reference for future module boundaries, provider ports and security/commerce/gamification extension points.

It is deliberately **not** a complete fitness application. Features such as production authentication, Health Connect, Garmin/YAZIO/RENPHO, nutrition tracking, XP, quests, streaks, boss events, social functions, commerce, advertising, AI, Play Integrity and the administration portal are not claimed as implemented.

## 2. Delivered repository

The monorepository contains:

- a modular Android client scaffold under `android/`;
- a runnable FastAPI modular-monolith backend under `backend/`;
- PostgreSQL and Redis development services in `docker-compose.yml`;
- an initial Alembic migration;
- a small architecture-complete vertical slice;
- deterministic provider mocks and provider contract tests;
- CI, linting, type checking, test commands and Windows alternatives;
- self-authored technical demo fixtures with provenance records;
- architecture, API, data-model, security, privacy, integrations and testing documentation;
- ten Architecture Decision Records;
- a phased implementation roadmap.

## 3. Technology baseline

| Area | Selected foundation |
|---|---|
| Android language/UI | Kotlin 2.3.21, Jetpack Compose BOM 2026.06.00, Material 3 |
| Android build | AGP 8.13.2, Gradle 8.13, Kotlin DSL, version catalog, JDK 17 |
| Android data/background | Room 2.8.4, DataStore 1.2.1, WorkManager 2.11.2 |
| Android DI/network | Hilt 2.58, AndroidX Hilt 1.3.0, Retrofit 3.0.0, Kotlin Serialization |
| Backend | Python 3.12+, FastAPI, Pydantic 2, SQLAlchemy 2, Alembic |
| Persistence/infrastructure | PostgreSQL 17, Redis 8, Docker Compose |
| Backend quality | uv lockfile, Ruff, strict mypy, pytest, httpx, coverage |
| CI/security | GitHub Actions, dependency review, pip-audit, Trivy, Gitleaks |

Version declarations are centralized in `android/gradle/libs.versions.toml`, `backend/pyproject.toml`, `backend/uv.lock` and `docker-compose.yml`.

## 4. Functional vertical slice

### Android

The Android scaffold implements the following local/offline path:

1. create and edit one guest profile;
2. persist the profile in Room across process restarts;
3. create, list, edit and soft-delete private custom exercises;
4. create, list, start and complete minimal workouts;
5. emit typed in-process domain events, including a deterministic `WorkoutCompleted` event;
6. write each mutation and its outbox operation in one Room transaction;
7. schedule synchronization through WorkManager;
8. acquire and store a development guest token;
9. push pending operations through a replaceable Retrofit sync client;
10. mark acknowledged outbox and aggregate rows as synchronized.

The UI uses Compose, Material 3, Navigation Compose, MVVM and unidirectional state. Room remains the local source of truth; UI code does not depend directly on Retrofit.

### Backend

The backend implements:

- `GET /health`;
- development guest-session creation;
- authenticated guest profile read/update;
- custom-exercise CRUD;
- workout list/create/update/start/complete;
- `POST /api/v1/sync/push`;
- UUID upserts and idempotency-key handling;
- request IDs and a common error envelope;
- domain/application/infrastructure/presentation separation;
- repository and unit-of-work ports;
- SQLAlchemy 2 persistence adapters;
- an in-process typed event dispatcher;
- a database outbox foundation;
- deterministic external-provider mocks;
- a domain-module dependency catalog.

The guest token is intentionally a development mechanism and is not production-ready authentication.

## 5. Architecture decisions

The most important decisions are documented in `docs/adr/`:

1. monorepository;
2. modular monolith before microservices;
3. Android MVVM with unidirectional state;
4. Room as local source of truth;
5. transactional outbox synchronization;
6. FastAPI and SQLAlchemy 2;
7. ports and adapters for external providers;
8. test pyramid and contract tests;
9. UUID and UTC time strategy;
10. isolated development guest-authentication foundation.

Long-term domains from the product reference are represented through documented module boundaries and dependency rules rather than empty packages. External systems are represented by interfaces such as `HealthDataProvider`, `NutritionProvider`, `ActivityProvider`, `BodyMeasurementProvider`, `AuthenticationProvider`, `PaymentProvider`, `AdvertisementProvider`, `NotificationProvider`, `IntegrityProvider` and `AiProvider`.

## 6. Data and synchronization model

Both client and server use UUID identifiers and UTC timestamps. The Android entities include:

- optional server ID;
- synchronization status;
- retry count and last error in the outbox;
- optional conflict version;
- created/updated timestamps.

Supported local synchronization states are:

- `LOCAL_ONLY`;
- `PENDING`;
- `SYNCING`;
- `SYNCED`;
- `FAILED`;
- `CONFLICT`.

The current synchronization direction is push-only. Pull synchronization, tombstone reconciliation, conflict presentation and account-merging rules are specified but not implemented.

## 7. Tests and checks actually executed

The following results were produced in the generation environment and re-verified in WSL on 2026-07-10. They are reported separately from checks that could not run.

### Successfully executed

From `backend/`:

```text
uv lock --check
uv run ruff format --check .
uv run ruff check .
uv run mypy src
uv run pytest
uv run alembic heads
```

Results:

- dependency lock check: passed;
- the lockfile resolves through public PyPI and contains no environment-specific internal registry URLs;
- Ruff formatting: passed;
- Ruff linting: passed;
- mypy strict type checking: passed;
- pytest: **11 tests passed**;
- measured backend coverage: **76.11%**;
- configured coverage floor: 70%;
- Alembic current head: `08adec2dab35`;
- migration-on-empty-database test: passed;
- idempotency tests: passed;
- provider contract tests: passed;
- module-cycle test: passed.
- repository integrity check passed after local backend and Android build artifacts were generated.

Runtime smoke checks also passed:

- the initial migration was applied to a temporary SQLite database;
- Uvicorn started successfully;
- `/health` returned application and database status;
- OpenAPI generation succeeded with ten route paths;
- the backend-only E2E flow succeeded:
  `guest session -> custom exercise -> workout -> start -> complete`.

The generated OpenAPI document is stored in `shared/openapi.json`.

### Android checks successfully executed

From `android/`, with `ANDROID_HOME=/home/student/Android/Sdk`:

```text
./gradlew spotlessCheck detekt test lintDebug assembleDebug
```

Results:

- the pinned Gradle 8.13 bootstrap download and SHA-256 verification succeeded;
- Spotless formatting checks passed;
- Detekt passed;
- Android JVM unit tests passed;
- Android Lint passed;
- the debug APK build passed;
- Gradle reported `BUILD SUCCESSFUL` with 567 actionable tasks (26 executed and 541 up-to-date in the final run);
- the Room version-1 schema was exported to `android/core/database/schemas/`.

### Checks not executable in the generation environment

Room instrumentation tests, Compose UI tests and navigation instrumentation tests were not run because no emulator or connected Android device was available. The remaining command is:

```bash
cd android
./gradlew connectedDebugAndroidTest
```

Docker and Docker Compose were unavailable inside distribution `ISP2025` because Docker Desktop WSL integration was disabled. Therefore, the full three-service stack and Docker image build were not executed in this verification pass. The expected commands are:

```bash
cp .env.example .env
docker compose up --build
docker compose exec backend alembic upgrade head
docker compose exec backend pytest
```

## 8. Security status

Implemented foundations:

- no committed secrets;
- `.env.example` and environment-specific settings;
- validation at API and domain boundaries;
- request IDs and structured logs;
- common non-sensitive API errors;
- CORS configuration;
- process-local development rate limiter with a documented Redis replacement path;
- guest/future-user authentication separation;
- idempotency records and unique constraints;
- database constraints and foreign keys;
- secret and dependency scanning in CI;
- documented threat model.

Not implemented and not claimed complete:

- production token issuance/rotation/revocation;
- Google or e-mail account linking;
- Play Integrity verification;
- certificate pinning;
- purchase/ad verification;
- fraud scoring or server-authoritative rewards;
- production privacy/legal review.

## 9. Demo-data status

All included exercise, muscle, equipment and workout fixtures are self-authored technical demo records. They are marked as demo data and are not represented as medically, scientifically or professionally reviewed fitness guidance. No foreign images or videos are included. Provenance and intended use are documented in `data/licenses/`.

## 10. Known limitations

- Android instrumentation tests still require an emulator or connected device.
- Synchronization is push-only and uses a development guest token.
- There is no production account conversion or merge workflow.
- There is no pull synchronization, user-visible conflict resolver or remote tombstone processing.
- Redis is used as a health/rate-limit foundation, not yet as a full job queue or leaderboard store.
- Admin is an audited delivery boundary and documentation placeholder, not an implemented portal.
- Long-term product modules are interfaces/specifications only unless listed in the functional slice.
- The project license must be selected before public distribution.

## 11. Mock-provider inventory

The backend provides deterministic, clearly named test adapters for the prepared provider contracts:

- `MockHealthDataProvider`;
- `MockNutritionProvider`;
- `MockActivityProvider`;
- `MockBodyMeasurementProvider`;
- `MockAuthenticationProvider`;
- `MockPaymentProvider`;
- `MockAdvertisementProvider`;
- `MockNotificationProvider`;
- `MockIntegrityProvider`;
- `MockAiProvider`.

They return fixed, automation-friendly values and are covered by provider contract tests. These adapters are test doubles only; no production provider or completed product feature is implied.

## 12. Post-scaffold synchronization update

The private-exercise synchronization slice now includes cursor pull, tombstone application and
user-facing conflict resolution. Room schema version 2 adds persisted local/remote conflict
snapshots. A revision mismatch carries the remote exercise in the backend sync response, so the
client marks the stale outbox operation as `CONFLICT` instead of retrying it indefinitely.

The Android UI displays a conflict badge, version comparison and explicit keep-local, take-server
or manual-merge choices. Local/merge operations use the remote revision as their new base and the
conflict closes only after a successful server acknowledgment. Backend coverage includes the
returned remote snapshot contract.

AndroidX Test dependencies resolve to AndroidX Test Core/Runner/Rules 1.7.0, Ext JUnit 1.3.0 and
Espresso 3.7.0. The repository provides `connectedProjectAndroidTest` for the two real modules and
`scripts/android-connected-tests.sh` for direct Windows-emulator/WSL ADB execution. The direct
runner records raw results and avoids UTP installation when emulator-console authentication is not
available.

## 13. Test-environment stabilization update

The standard local workflow is `make test` for all backend and Android JVM checks and
`make android-connected-test` for the three non-empty instrumentation modules
(`core:database`, `data`, `app`). The latter verifies SDK/ADB/device prerequisites, requires one
device or an explicit `ANDROID_SERIAL`, builds and installs APKs directly through ADB, writes a
timestamped raw log and uses a 180-second per-runner timeout. It does not report empty modules as
tested.

The new `data` instrumentation suite covers atomic server adoption, keep-local pending
confirmation and manual merge queueing for persisted exercise conflicts. The suite compiles with
the Android test APK. Execution remains blocked in this workstation session: the Windows SDK has
only Android 17/API 37 preview, while the stable API-36 image is installed inside WSL and cannot
be used as a Windows emulator system directory. The preview image previously exposed an Espresso
`InputManager.getInstance` incompatibility and is intentionally not accepted as the baseline.

Docker Compose was verified with PostgreSQL, Redis and backend healthy; the container migration
head is `4f3b20b5b92a`, the smoke flow passed, and Gitleaks found no secrets in the repository.

## 14. Recommended next Codex assignments

### Assignment 1 — Complete bidirectional offline synchronization

Implement pull sync, remote tombstones, cursor-based change feeds, conflict detection and deterministic conflict rules for guest profile, custom exercises and workouts. Add WorkManager tests, Room migration tests, retry/backoff tests and a user-visible conflict state. Preserve Room as source of truth and maintain idempotency.

**Definition of done:** Android and backend tests cover offline edits, concurrent edits, deletion conflicts, retry after network failure and successful reconciliation without data duplication.

### Assignment 2 — Production account and guest-conversion slice

Implement Credential Manager/Google sign-in on Android, backend Google token validation, refreshable application sessions, account linking and transactional migration of guest-owned records. Add anti-enumeration behavior, audit logs, replay protection and explicit merge-conflict handling.

**Definition of done:** an existing offline guest can authenticate and retain all profile, exercise and workout data; duplicate conversion requests are idempotent; security tests and documentation are complete.

### Assignment 3 — Licensed exercise catalog and workout-planning slice

Introduce reviewed catalog entities, muscle/equipment relationships, source/license metadata, seed/import validation and one editable workout-plan vertical slice. Keep custom exercises private and separate from public catalog content. Add filters by equipment/location and deterministic replacement suggestions.

**Definition of done:** licensed sources are traceable, imports are reproducible, Android catalog browsing works offline, plan editing is covered by tests and no unlicensed media enters the repository.

## 13. Reproduction checklist

1. Copy `.env.example` to `.env`.
2. Run `make setup` or `scripts/dev.ps1 setup`.
3. Start PostgreSQL, Redis and backend through Docker Compose.
4. Apply `make migrate`.
5. Run `make backend-lint` and `make backend-test`.
6. Open `android/` in Android Studio with JDK 17 and SDK Platform 36.
7. Run Android checks and install the debug app on API 28+.
8. Execute the local guest/exercise/workout flow.
9. Run `python scripts/smoke_test.py` against the backend.
10. Review `SECURITY.md`, `PRIVACY.md` and all accepted ADRs before extending production-sensitive functionality.

## Offline exercise catalog foundation

Implemented a self-authored CC0 three-record dataset through a validated atomic backend importer and reviewed-only API into an offline-first Android Room 3 cache. Android provides public list/detail, combined filters, loading/empty/error/offline states and provenance, structurally separated from private exercises. Guest upload now requires opt-in.

Backend head is `c1a4e6d91b0f`; Android schema is 3. The backend suite collects 24 tests and reports 76.98% coverage. Connected execution alone remains externally blocked by the absent stable Windows API-36 system image/AVD; instrumentation code compiles independently.
