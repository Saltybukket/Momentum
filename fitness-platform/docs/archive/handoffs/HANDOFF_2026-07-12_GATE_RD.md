# Momentum handoff — Gate R/D

Date: 2026-07-12  
Branch: `codex/fix-scaffold-reproducibility`  
Gate-D implementation head before this handoff: `955be1df3ed4135922b6115cecc3d3a3b24f78f6`

## Completed scope

Gate R and Gate D from `08_Momentum_Next_Codex_Prompt_2026-07-12.md` are complete. Gate E/F/G and product/UI work were not started.

- Sync replay hashes canonical compact UTF-8 JSON containing contract version `1`, entity type, action and typed payload.
- Expired processed operations are atomically reusable under a row lock; bounded cleanup remains maintenance only.
- `CANCELLED` and `COMPLETED` workouts are terminal. Start is valid only from `PLANNED` and idempotent in `IN_PROGRESS`.
- CRUD and Sync share single-line/multiline control/Bidi validation.
- CI supplies a real PostgreSQL Pytest URL and uses a separate migration database.
- HTTP success ends at durable commit; in-process handler failures cannot change a committed response.
- DB outbox states are `PENDING`, `PROCESSING`, `FAILED`, `PROCESSED`, `DEAD_LETTER`.
- PostgreSQL claims use bounded `FOR UPDATE SKIP LOCKED` batches. Claims have owner and five-minute lease; stale leases are reclaimable.
- Retry uses exponential backoff capped at one hour; error storage is limited to the error type and 1,000 characters; five attempts dead-letter.
- `make outbox-process` runs a bounded batch and returns machine-readable counts.
- Client request IDs accept `[A-Za-z0-9._:-]{1,64}`; invalid values are replaced by a server UUID.
- Unexpected errors return the generic `INTERNAL_ERROR` envelope and do not reflect exception/SQL/secret text.

## Verification

- Backend: `87 passed in 89.95s` on the final source state, including `11` PostgreSQL-specific tests.
- Combined coverage: `78.21%`; statements: `82.60%` (`1927/2333`); branches: `49.15%` (`173/352`).
- New `application/outbox.py`: `85%` combined coverage.
- Ruff format/check and mypy: passed.
- Alembic head: `e15b7c9d420f`.
- Fresh SQLite upgrade/check: passed.
- Fresh isolated PostgreSQL upgrade/check: passed.
- OpenAPI export: no drift.
- Android `spotlessCheck detekt test lintDebug assembleDebug`: passed in `24.81s`, 567 tasks (7 executed, 560 up-to-date).
- Android debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`, 39,897,735 bytes, SHA-256 `46e57c85b2b172abc0115fd6d6acad8ff4e673810acb6f590ec33b2c679ce81d`.
- Android JVM reports contain 8 successful test executions. Connected tests were not run.
- AndroidTest sources compiled during the baseline run: 182 tasks, successful in 33.67s.
- Docker image build: passed. Compose PostgreSQL, Redis and backend: healthy. Smoke flow: passed.
- Repository integrity and `git diff --check`: passed.
- Gitleaks 8.28.0: 25 commits / about 3.78 MB scanned, no leaks.
- Trivy 0.66.0 filesystem scan: zero HIGH/CRITICAL findings in the locked runtime dependencies.
- GitHub Actions run `29209436534`: `https://github.com/Saltybukket/Momentum/actions/runs/29209436534`. Backend, Android and repository-security jobs all passed for implementation head `955be1d`; the backend job executed the real PostgreSQL regression matrix.

Docker Desktop's WSL credential configuration attempted to execute `docker-credential-desktop.exe` as a Linux binary. The verified workaround was an external temporary `DOCKER_CONFIG` containing `{}` at `/tmp/momentum-docker-config`; nothing was added to Git.

## Commits

- `ac0c77a` — `fix(sync): bind replay semantics to typed operations`
- `93ea8ca` — `ci: execute postgres integration regressions`
- `51187f0` — `fix(events): deliver committed outbox events reliably`
- `7e7c8da` — `fix(api): bound request context and internal errors`
- `955be1d` — `docs: record gate r and outbox contracts`

## Remaining risks and next slice

- Delivery is intentionally at-least-once after process crashes; every future production side-effect handler needs its own durable idempotency key/ledger.
- A scheduler/process supervisor must invoke `outbox-process` continuously in deployed environments.
- The CI emits only a Node.js-20 action deprecation warning; jobs currently run successfully under the forced Node.js 24 runtime.
- The stable Windows API-36 AVD remains the external blocker for connected acceptance tests only.
- General documentation staleness remains the separately scoped Gate-H cleanup.

Resume with Gate E only: Room-atomic pull cursor, consent enqueue, Android Keystore secret storage and Android UI-state hardening. Do not combine Gate E with catalog release Gate F.
