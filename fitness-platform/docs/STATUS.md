# Project status

Status basis: 2026-07-15, branch `codex/fix-scaffold-reproducibility`. Gate 20C Visual Sprint 1
is locally accepted at code head `9321e83`; final documentation and CI verification follow this
code checkpoint. The stable Windows API-36 acceptance device was `emulator-5554`, Android 16.

## Implemented

- Offline-first Android guest profile, private custom exercises and minimal workout lifecycle.
- Owner-scoped private-exercise push/pull sync with tombstones, durable conflicts, Room cursor
  checkpointing, token renewal and explicit consent cancellation.
- FastAPI modular monolith with development guest recovery, idempotency, reliable leased outbox,
  private exercise/workout APIs and PostgreSQL persistence.
- Immutable authoritative public catalog releases with validated import, atomic activation,
  retained rollback history, order-independent canonical hashing, data-preserving downgrade,
  public page/detail/facet/snapshot APIs and Android Room cache.
- Explicit Privacy-screen recovery from rejected/invalid guest credentials without deleting local
  data, plus owner-bound Android outbox success/failure/conflict finalization.
- Momentum App Shell V1 with Material 3 light/dark theming, compact bottom navigation, expanded
  navigation rail and four resource-backed roots. The local dashboard exposes only real Room and
  sync state, including active/recent workouts, conflicts and offline readiness.
- Gate-Q quality hardening uses visible self-authored root icons, route-family selection,
  resource-backed catalog semantics and structured catalog-import validation errors.
- Phase 2A.1 adds local-first training locations, a stable equipment registry, editable presets and
  equipment-aware public-catalog compatibility/alternatives. The local model is sync-capable but
  no backend location sync contract is implemented or claimed.
- UX.0 preserves Room data on unsupported downgrade, hardens single-line location names and finite
  save/error operations, makes active switching revision-bound and distinguishes catalog location
  requirements from genuinely missing equipment.
- The premium Android presentation foundation centralizes navy/silver/amber light/dark tokens and
  reusable responsive components. Existing screens use honest local state, accessible labels and
  explicit empty/loading/action states. The self-authored adaptive/monochrome launcher mark is a
  geometric Momentum M with restrained forward movement.
- Gate UI.1 completes explicit Material 3 roles and contrast assertions, production-composable
  previews, root/subpage navigation semantics, scroll-safe critical screens and API-28 splash
  branding. Room startup now fails closed before feature UI and sync, with a data-preserving retry.
- Phase 2B.1 adds editable owner-scoped offline training plans, typed public/private exercise
  references with durable snapshots, atomic copy/activation/archive/delete operations, Room-backed
  plan editing and idempotent self-authored starter plans. The immutable workout snapshot boundary
  is defined without beginning workout execution or plan synchronization.
- Phase 2B.2 adds Room-backed recurring schedules, civil-time dated occurrences, availability and
  overrides, deterministic rolling 56-day materialization, explicit one-off versus future edits
  and derived conflicts. Phase 2B.2.1 makes schedule materialization, permanent-rule replacement
  and plan/schedule lifecycle switches atomic; replacement setup preserves the old active state
  until confirmation. Conflict queries include adjacent days without leaking them into visible
  output, and Room 9 retains durable snapshot/origin identity. Workouts expose Today, Calendar,
  Plans and History without beginning workout execution or calendar synchronization.
- Gate 18D stabilizes Workout History detail and Repeat as an owner-scoped, transactionally safe
  planned-workout clone. Repeat preserves ordered exercise references, ignores immediate duplicate
  submissions, waits for durable persistence before navigation and is unavailable when current
  exercise data cannot resolve every reference. Workout lifecycle reads and writes are owner-bound,
  and create/start/complete validation, persistence and outbox recording share Room transactions.
  This is not full workout execution: detail names are resolved from current exercise data rather
  than immutable execution snapshots.
- The local Visual Sprint 1 candidate hardens responsive tracking controls, conflict resolution,
  workout-detail state rendering, truthful home actions and route-family navigation. Dialogs now
  remain open until persistence succeeds, conflict completion callbacks fire exactly once, and
  keep-local conflict rows survive exercise updates until server acknowledgement.
- Repository, migration, static-analysis, JVM/build and container verification workflows.

## Schema and verification authority

- Alembic head: `0d4f6a8b2c17`.
- Android Room version: 9; exported schemas 1–9 are committed.
- The canonical command matrix and connected-test limitation are in [TESTING.md](../TESTING.md).
- The Gate 20C matrix passes 101 distinct Android JVM tests (176 debug/release task executions)
  with zero failures, errors or skips. `spotlessCheck detekt test lintDebug assembleDebug` passed
  with 656 actionable tasks (48 executed, 4 from cache and 604 up-to-date), and every AndroidTest
  source set compiles (`core:sync` is intentionally `NO-SOURCE`).
- Stable API-36 connected acceptance executed 87 tests: database 20/20, datastore 8/8, data 36/36,
  feature 14/14 and app 9/9. The strict parser verified exactly one successful completion per
  module; raw evidence is `android/build/connected-test-results/20260715T162301Z.txt`.
- Backend PostgreSQL verification passes 129 tests at 79.24% combined coverage. PostgreSQL and
  SQLite Alembic upgrade/check pass at head `0d4f6a8b2c17`.
- Docker Compose config/build/up, container migration and smoke passed. Gitleaks found no secrets;
  Trivy found no fixed HIGH/CRITICAL filesystem or runtime-image vulnerabilities. The current
  runtime-image report retains 22 unfixed/deferred HIGH/CRITICAL Debian findings for review.
- Gate 18D implementation CI
  [run 29406924215](https://github.com/Saltybukket/Momentum/actions/runs/29406924215), attempt 1,
  passed Android, backend and repository-security for exact head `4993310` without reruns. Full
  local evidence is in [the Gate 18D completion handoff](HANDOFF_2026-07-15_GATE_18D_WORKOUT_REPEAT.md).

## Active risks

- Guest identity and explicit replacement remain a development contract, not production
  authentication, revocation or account linking. Replacing local credentials does not delete
  previously stored server data.
- Gate 20C is locally accepted, but the final documentation commit is not accepted remotely until
  its exact pushed SHA has a green GitHub Actions run. API-37 preview results remain diagnostic
  history only.
- Owner scoping is enforced at the Workout repository boundary; other private-data repositories
  still require their own later identity-boundary audit.
- Health, nutrition, rewards, social, commerce, AI and production provider integrations are not
  implemented.
- CI records unfixed HIGH/CRITICAL Trivy findings as artifacts; only findings with an available
  fix fail the gate. No vulnerability exception is silently ignored.

## Next phase

Commit the final Gate 20C evidence, push and verify GitHub Actions for the exact final SHA. Only
after that remote acceptance may the next explicitly authorized gate begin. Full workout
execution, calendar sync, gamification and optional location synchronization remain outside this
gate.
