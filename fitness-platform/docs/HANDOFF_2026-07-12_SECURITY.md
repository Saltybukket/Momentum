# Momentum security hardening handoff — 2026-07-12

## Checkout and completed commits

- WSL: `ISP2025`
- Repository: `/home/student/projects/Momentum`
- Branch: `codex/fix-scaffold-reproducibility`
- Starting HEAD: `98168e2616f2c87db985070007f2a826bf7e8ba1`
- `b75e7aa test(backend): enforce database parity regressions`
- `8573ae4 fix(sync): enforce typed owner-scoped operations`
- `fe99356 chore(api): refresh typed sync contract`
- `8d565b2 fix(workouts): enforce lifecycle and safe exercise links`

## Gate A — complete

- SQLite connections enable and assert `PRAGMA foreign_keys=ON`.
- Revision `c83f7a6d219b` purges ambiguous legacy idempotency rows, including a raw key of `"a" * 64`; ADR-011 records why 24-hour replay rows are intentionally purged.
- Previous guest concurrency, transactional idempotency and catalog scale regressions remain green.

## Gate B — complete

- Sync transport is a closed Pydantic union for Profile UPSERT, Exercise UPSERT/DELETE and Workout UPSERT/START/COMPLETE.
- Payloads forbid extras and enforce UUIDs, enums, required fields, field lengths, unique workout links and control/Bidi policy.
- Application code receives typed domain commands rather than free dictionaries.
- Private workout references are owner-scoped and reject foreign, missing, deleted and public-catalog IDs without leaking ownership; SQLite and PostgreSQL tests pass.
- `(owner_user_id, operation_id)` provides transactional replay/mismatch protection across HTTP batches and concurrent PostgreSQL requests.
- Processed operations expire after seven days; bounded cleanup is `make sync-operation-cleanup`.
- Migration/active Alembic head: `d94a1f6c730e`.
- ADR: `ADR-012-typed-owner-scoped-sync.md`.

## Gate C — complete

- General workout writes no longer accept status.
- Lifecycle is `PLANNED -> IN_PROGRESS -> COMPLETED`; completion requires start, records both timestamps, emits one deterministic durable completion event, and is terminal.
- Sync uses explicit `START` and `COMPLETE` operations. Android outbox types and payload generation match this contract.
- Link replacement clears and flushes before reinsertion, eliminating transient unique-position failures.
- Owner IDs are validated in one set query; maximum 50 unique active private exercises.
- SQLite and PostgreSQL cover unchanged/add/remove/reorder/empty links, duplicates, excessive links, foreign IDs and lifecycle bypasses.
- ADR: `ADR-013-workout-lifecycle.md`.

## Last verified results

- Ruff format/check: passed.
- mypy: passed for 40 source files.
- Backend full suite with real PostgreSQL: **65 passed**.
- Coverage: **77.08%** (floor 70%).
- Fresh SQLite migration/check: passed.
- Fresh separate PostgreSQL database `fitness_migrations`: migration/check passed.
- Alembic head: `d94a1f6c730e`.
- Android: Spotless, JVM tests, `core:model`, `core:sync` and `data` compilation passed (363 tasks in the final affected build).
- Repository integrity and `git diff --check`: passed.
- OpenAPI regenerated after typed sync/workout commands.
- Connected tests were not run.

The earlier PostgreSQL `DuplicateTableError` was not a code failure: Pytest `create_all` and Alembic were accidentally run concurrently against the same `fitness` test database. A subsequent serial migration on the fresh separate `fitness_migrations` database passed through head with no drift. Keep migration and integration suites on separate databases or execute them serially.

## Resume point — begin Gate D only

Do not reopen A–C unless a regression fails. Next:

1. Add the failing post-commit handler regression.
2. Make HTTP success depend only on the durable commit; handler failure must not turn it into 500.
3. Implement the DB-outbox processor with claim/lease, retry/backoff, max attempts and failed/dead-letter state.
4. Add the bounded/safe request-ID and generic 500 envelope from Gate D3.
5. Run the full PostgreSQL/backend/Android matrix, commit and push Gate D.

After D, continue in order with Gate E (Room cursor, consent, Keystore), Gate F (catalog release immutability/activation/removal), then optional Gate G. No design system, App Shell or new product features yet.

Gradle audit ZIP remains external and must not be committed:

- `/home/student/audit-artifacts/gradle-8.13-bin.zip`
- `C:\Users\tfeic\Downloads\gradle-8.13-bin.zip`
- SHA-256 `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`
