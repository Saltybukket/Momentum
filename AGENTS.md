# AGENTS.md

## Project and paths

Momentum is a monorepository for an offline-first Android fitness platform and a FastAPI modular-monolith backend. Work from WSL distribution `ISP2025` and use Linux paths for Git, Gradle, Python, Docker and scripts.

- Repository root: `/home/student/projects/Momentum`
- Application workspace: `/home/student/projects/Momentum/fitness-platform`
- Android: `fitness-platform/android/`
- Backend: `fitness-platform/backend/`
- Demo/seed data: `fitness-platform/data/`
- Infrastructure and CI: `fitness-platform/infrastructure/`, `fitness-platform/docker-compose.yml`, `fitness-platform/.github/`
- ADRs and specifications: `fitness-platform/docs/`

## Authoritative references

Apply requirements in this order:

1. The current concrete task.
2. `architecture/architektur-prompt.md` for the scaffold and technical architecture.
3. `architecture/master-prompt.md` for mandatory long-term product scope.
4. `Research/FITNESS_RESEARCH_REFERENCE.md` for exercise, anatomy and seed-data work.
5. Accepted ADRs and documented decisions.
6. Existing code as an implementation starting point, not as authority.

Read this file, every more specific `AGENTS.md`, `fitness-platform/ARCHITECTURE.md`, the relevant module documentation and tests before editing. For conflicts, document the conflict and record material architecture changes in an ADR; never diverge silently.

## Architecture rules

- Preserve the monorepository, Android Clean Architecture/MVVM/UDF, Room source of truth, offline-first outbox, FastAPI modular monolith, ports-and-adapters and typed domain events.
- Keep domain code independent of Compose, Room, Retrofit, FastAPI, SQLAlchemy and concrete vendors.
- Keep clocks and UUID generation injectable in business logic.
- Maintain stable UUID/idempotency semantics and server authority for rewards, commerce and anti-cheat-sensitive values.
- Avoid cyclic dependencies, global mutable state, duplicated models without a boundary reason and business logic in UI/API layers.
- Use official provider APIs only. Production adapters must remain replaceable through ports.

Allowed changes are focused vertical slices, necessary scaffold repairs, tests, migrations and matching documentation. Do not add broad placeholder features, empty modules, unlicensed assets, secrets, generated build output, local databases or unrelated dependencies. Do not remove master-prompt requirements or move them to `FUTURE_FEATURES.md`.

## Workflow

1. Check `git status --short`, branch, recent history, repository structure, applicable ADRs and existing tests.
2. Select one small vertical slice from the task, architecture prompt or `IMPLEMENTATION_PLAN.md`.
3. Preserve unknown local changes and do not include them in commits.
4. Implement through all affected layers and add deterministic tests.
5. Run the affected format, lint, type, test, migration and build commands.
6. Run `git diff --check`, review the diff and update behavior/setup documentation.
7. Commit a verified logical unit with a Conventional Commit message. Push only to a configured remote without force.

Never use destructive Git commands such as `git reset --hard`, `git clean -fd`, force-push or history-rewriting operations without explicit authorization.

## Commands

Run from `fitness-platform/` unless noted:

```bash
make setup
make backend
make backend-test
make backend-lint
make android-test
make android-build
make test
make lint
make migrate
make seed
make openapi
make smoke
make dev
```

Direct backend checks:

```bash
cd backend
uv sync --extra dev --frozen
uv run ruff format --check . ../scripts
uv run ruff check . ../scripts
uv run mypy src
uv run pytest
uv run alembic upgrade head
uv run alembic check
```

Direct Android checks:

```bash
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
cd android
./gradlew spotlessCheck detekt test lintDebug assembleDebug
./gradlew connectedDebugAndroidTest  # emulator/device required
```

Docker and migrations:

```bash
cp .env.example .env
docker compose config
docker compose up --build
docker compose exec backend alembic upgrade head
```

Windows PowerShell alternatives are in `fitness-platform/scripts/dev.ps1`.

## Definition of done

A slice is done only when behavior is real across affected layers, relevant tests pass, lint/static analysis passes, the affected build succeeds, migrations and Room schemas are handled where relevant, error states are covered, mocks are clearly named, documentation is current, and no secrets or local/generated artifacts remain.

Report exact commands and results. If a check cannot run, record its command, real error and whether the cause is code or environment. Do not claim unexecuted tests, builds, integrations or coverage.

## Mocks, external blockers and documentation

- Deterministic mocks must be visibly named `Mock`/`Fake`, covered by contracts and never described as production integrations.
- Missing credentials, vendor approval, emulator/device access or Docker integration are external blockers; keep production seams intact and document activation steps.
- Exercise/health data needs stable schemas, provenance, license and attribution before import. Use only marked technical demo data until the import pipeline is stable.
- Update `README.md`, `ARCHITECTURE.md`, API/data/security/testing docs, ADRs, Room schemas, OpenAPI and `docs/IMPLEMENTATION_REPORT.md` whenever the corresponding contract or status changes.
