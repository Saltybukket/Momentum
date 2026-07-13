# AGENTS.md

## Project and operating environment

Momentum is a monorepository for an offline-first Android fitness platform and a FastAPI modular-monolith backend.

Use only the WSL2 distribution `ISP2025` for repository work.

Canonical paths:

- Repository root: `/home/student/projects/Momentum`
- Application workspace: `/home/student/projects/Momentum/fitness-platform`
- Android: `fitness-platform/android/`
- Backend: `fitness-platform/backend/`
- Demo and catalog data: `fitness-platform/data/`
- Infrastructure: `fitness-platform/infrastructure/`, `fitness-platform/docker-compose.yml`
- CI: `.github/workflows/`
- ADRs and specifications: `fitness-platform/docs/`
- Product master specification: `architecture/master-prompt.md`
- Historical scaffold assignment: `architecture/architektur-prompt.md`
- Research reference: `Research/FITNESS_RESEARCH_REFERENCE.md`

Use Linux paths and Linux Git, Gradle, Python, Docker and shell tools. Do not perform Momentum work from the Ubuntu WSL distribution or from a duplicate Windows checkout.

## Authority order

Apply requirements in this order:

1. The current explicit task and its allowed scope.
2. Accepted ADRs, generated schemas, OpenAPI and other current canonical contracts.
3. `fitness-platform/docs/STATUS.md` and the newest handoff for the active branch.
4. `architecture/master-prompt.md` as the mandatory long-term product specification.
5. `Research/FITNESS_RESEARCH_REFERENCE.md` only for exercise, anatomy, provenance and seed-data work.
6. `architecture/architektur-prompt.md` as a historical scaffold reference.
7. Existing code as an implementation starting point, not as authority.
8. Older handoffs and archived reports as history only.

A historical prompt or report must never override a newer accepted ADR or verified contract.

Read this file, the newest active status/handoff, applicable ADRs, relevant tests and module documentation before editing. Record material architecture decisions in an ADR. Never resolve a conflict silently.

## Stable architecture invariants

- Preserve the monorepository, Android Clean Architecture/MVVM/UDF, Room source of truth, offline-first outbox, FastAPI modular monolith, ports-and-adapters and typed domain events.
- Domain code must not depend on Compose, Room, Retrofit, FastAPI, SQLAlchemy or concrete vendors.
- Clocks and UUID generation remain injectable.
- Private data is owner-scoped at every query and write boundary.
- Private sync is explicit-consent only and defaults to disabled.
- Applying a remote page and advancing its cursor must be one Room transaction.
- Bearer tokens and recovery secrets must never be stored in plaintext, logged, returned in errors or persisted in WorkManager output.
- Durable backend events use at-least-once delivery. Every real side-effect handler must be durably idempotent.
- Outbox claims and finalization must reject stale workers and stale claim tokens.
- Completed and cancelled workouts are terminal.
- Catalog manifest, canonical hash, active release and served content must never silently diverge.
- Catalog hashes use one documented semantic canonicalization across importer, API and Android; unordered arrays are normalized before hashing.
- Root navigation selection is derived from documented route families, not exact leaf-route equality; training-location routes belong to Profile.
- Published catalog releases are immutable.
- Migration downgrades must preserve representable active data or fail before destructive changes; schema-only success is not enough.
- Server-authoritative rewards, commerce, anti-cheat and social-ranking values are never trusted from client counters.
- Production adapters use official provider APIs only and remain replaceable through ports.

Do not introduce cyclic dependencies, global mutable state, business logic in UI/API layers, generic JSON domain blobs or duplicate models without a boundary reason.

## Scope and gate discipline

- Work only on the gates or slices explicitly allowed by the current task.
- Do not begin a later gate until the current gate is green, reviewed, committed and pushed.
- Do not combine gates that the task deliberately separates.
- Preserve a clean recoverable checkpoint before broad migrations, security changes or new domains.
- If the remaining quota is near the task stop threshold, finish the current logical unit, test it, commit it, push it and write a handoff. Do not begin another gate.
- Do not add broad placeholder features, empty modules, speculative tables or unrelated dependencies.
- Features required by the master prompt remain mandatory product scope and must not be moved to `FUTURE_FEATURES.md`.
- `Research/` is read-only unless the current task explicitly authorizes research-data work.

Never use destructive Git commands such as `git reset --hard`, `git clean -fd`, force-push, rebase or history rewriting without explicit authorization.

## Standard workflow

1. Confirm WSL distribution, repository path, branch, HEAD and clean status.
2. Read the current task, newest status/handoff and applicable ADRs.
3. Reproduce the reported problem with a failing test.
4. Implement one focused vertical or hardening slice through all affected layers.
5. Run focused tests.
6. Run the complete relevant matrix.
7. Run migrations, OpenAPI/schema drift checks and build/security gates.
8. Review `git diff`, `git diff --check` and repository integrity.
9. Update canonical documentation.
10. Commit and push one verified logical unit.
11. Confirm the GitHub Actions run belongs to the final code commit.

Preserve unknown local changes and never include them in a commit.

## Backend commands

Run from `fitness-platform/backend/`:

```bash
uv sync --extra dev --frozen
uv run ruff format --check . ../scripts
uv run ruff check . ../scripts
uv run mypy src
```

Full backend suite must include a real dedicated PostgreSQL test database:

```bash
FITNESS_TEST_POSTGRES_URL=postgresql+asyncpg://<user>:<password>@localhost:5432/fitness_test \
  uv run pytest
```

Do not report the PostgreSQL matrix as passed if PostgreSQL tests were skipped.

Use a separate database for Alembic:

```bash
FITNESS_DATABASE_URL=postgresql+asyncpg://<user>:<password>@localhost:5432/fitness_migrations \
  uv run alembic upgrade head

FITNESS_DATABASE_URL=postgresql+asyncpg://<user>:<password>@localhost:5432/fitness_migrations \
  uv run alembic check

uv run alembic heads
```

Pytest and Alembic must never run concurrently against the same PostgreSQL database.

For SQLite migration verification, use a fresh temporary file and run both upgrade and check.

## Android commands

Run from `fitness-platform/android/`:

```bash
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}

./gradlew spotlessCheck detekt test lintDebug assembleDebug --no-daemon

./gradlew \
  :core:database:compileDebugAndroidTestKotlin \
  :core:datastore:compileDebugAndroidTestKotlin \
  :core:sync:compileDebugAndroidTestKotlin \
  :data:compileDebugAndroidTestKotlin \
  :app:compileDebugAndroidTestKotlin \
  --no-daemon
```

Connected execution:

```bash
./gradlew connectedProjectAndroidTest
```

or from `fitness-platform/`:

```bash
make android-connected-test
```

Connected tests may be reported as passed only when they actually ran on a device/AVD and produced test results. Compilation alone is not execution.

For Room changes:

- increment the Room version;
- provide explicit migrations;
- export and commit the schema JSON;
- compile AndroidTest sources;
- run migration/instrumentation tests when a valid device exists.

For secret-storage changes, test the real Android adapter on Android runtime in addition to JVM fakes whenever possible.

## Platform, contract and security checks

From `fitness-platform/`:

```bash
make openapi
git diff --exit-code shared/openapi.json
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
make smoke
```

From repository root:

```bash
python3 fitness-platform/scripts/check_repository.py
git diff --check
git status --short
```

Security verification must distinguish:

- source/filesystem scan;
- dependency scan;
- built-container-image scan;
- secret scan.

A green filesystem scan must not be described as a green image scan. Record fixed and unfixed findings separately.

## Testing rules

- Add deterministic tests for every corrected regression.
- Use real PostgreSQL for PostgreSQL-specific locking, uniqueness and concurrency.
- Use SQLite only where its behavior is intentionally supported.
- Use fakes for deterministic application tests and mocks only for narrow interaction assertions.
- Do not use arbitrary sleeps where a clock, event or virtual scheduler can express the behavior.
- Test success, validation, conflict, cancellation, process interruption and retry paths.
- For cross-platform hashes, test the exact serialized API response against the Android verifier; testing the source fixture alone is insufficient.
- Migration round trips must assert domain data, counts and hashes, not only successful commands.
- Validation tests assert stable error codes, field paths and state effects; they do not depend on
  optional validator ordering or complete human-readable messages.
- Measure combined, statement and branch coverage separately.
- Coverage percentage alone does not replace adversarial state-transition tests.
- Never claim unexecuted tests, integrations, migrations, scans or builds.

## Documentation rules

Canonical current documents are:

- `README.md`;
- `fitness-platform/README.md`;
- `fitness-platform/ARCHITECTURE.md`;
- `fitness-platform/API.md`;
- `fitness-platform/DATA_MODEL.md`;
- `fitness-platform/SECURITY.md`;
- `fitness-platform/PRIVACY.md`;
- `fitness-platform/TESTING.md`;
- `fitness-platform/INTEGRATIONS.md`;
- accepted ADRs;
- `fitness-platform/docs/STATUS.md`;
- the newest active handoff;
- generated Room schemas and `shared/openapi.json`.

Use `CHANGELOG.md` for notable changes and Git history for detailed history.

Do not append current status history to `docs/IMPLEMENTATION_REPORT.md`. That report is historical and should be archived. Older handoffs are historical and must not be used as current authority.

When behavior changes, update only the canonical documents affected by that behavior. Avoid copying test counts or migration heads into many files.

## Repository and artifact hygiene

Never commit or intentionally distribute:

- `.env`;
- real credentials or tokens;
- `local.properties`;
- `.venv`;
- `.gradle`;
- build directories;
- APKs unless explicitly requested as release artifacts;
- local databases;
- IDE state;
- downloaded Gradle distributions;
- coverage databases;
- secret-bearing logs.

Use a reproducible source-archive command based on tracked files or an explicit allowlist. Generate
final archive evidence only after the referenced Git head exists, and identify that head explicitly.

Demo data must be visibly synthetic and carry provenance/license metadata. No third-party exercise text, image or video may enter the repository without verified reuse rights.

## Definition of done

A slice is complete only when:

- behavior is implemented across every affected layer;
- targeted and full relevant tests pass;
- PostgreSQL-specific tests actually run;
- lint, formatting and type checks pass;
- Alembic and Room migrations are verified;
- OpenAPI and schemas have no unintended drift;
- Android builds succeed when affected;
- errors, cancellation and recovery paths are covered;
- source and built-image security results are reported honestly;
- canonical documentation is current;
- the logical unit is committed and pushed;
- the final CI run is tied to the correct commit and documented with run ID, URL, attempt and head SHA;
- `git status --short` is empty.

The final report must list exact commands, test counts, skipped tests, coverage, migration heads, Room version, APK hash when built, CI run ID, commits, push status and remaining risks.
