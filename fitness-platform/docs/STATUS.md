# Project status

Status basis: 2026-07-15, branch `codex/fix-scaffold-reproducibility`, local Gate 20C
candidate `d0dfcf6`. Gate 20C is not accepted or pushed because no stable API-36 Windows AVD is
installed.

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
- The Gate 20C candidate passes 101 distinct Android JVM tests (176 task/variant executions) with
  zero failures, errors or skips. `spotlessCheck detekt test lintDebug assembleDebug` passed with
  656 actionable tasks (62 executed, 594 up-to-date). Database, datastore, data, feature and app
  AndroidTest sources compile; the sync task succeeds with `NO-SOURCE`. On the non-acceptance
  API-37 preview AVD, database 20/20,
  datastore 8/8 and data 36/36 instrumentation tests passed. Feature and app Compose tests cannot
  initialize Espresso because that preview removed `InputManager.getInstance`; this evidence is
  not Gate 20C connected acceptance.
- Docker Compose config/build/up, container migration and smoke passed. Gitleaks found no secrets;
  Trivy found no fixed HIGH/CRITICAL filesystem or runtime-image vulnerabilities. The image report
  retains 20 unfixed HIGH/CRITICAL Debian findings for review.
- Gate 18D implementation CI
  [run 29406924215](https://github.com/Saltybukket/Momentum/actions/runs/29406924215), attempt 1,
  passed Android, backend and repository-security for exact head `4993310` without reruns. Full
  local evidence is in [the Gate 18D completion handoff](HANDOFF_2026-07-15_GATE_18D_WORKOUT_REPEAT.md).

## Active risks

- Guest identity and explicit replacement remain a development contract, not production
  authentication, revocation or account linking. Replacing local credentials does not delete
  previously stored server data.
- Stable Windows API-36 system image/AVD is unavailable. The installed API-37 preview AVD is not a
  substitute and is incompatible with the current Espresso runtime, so connected visual,
  accessibility and navigation acceptance is not claimed. Gate 20C commits remain local and no
  final CI run exists.
- Owner scoping is enforced at the Workout repository boundary; other private-data repositories
  still require their own later identity-boundary audit.
- Health, nutrition, rewards, social, commerce, AI and production provider integrations are not
  implemented.
- CI records unfixed HIGH/CRITICAL Trivy findings as artifacts; only findings with an available
  fix fail the gate. No vulnerability exception is silently ignored.

## Next phase

Install and run the stable Windows API-36 Google APIs/x86_64 AVD, execute the complete connected
matrix, then update evidence, push and verify CI for the exact final head. Do not begin Visual
Sprint 2 or another product gate before Gate 20C is accepted. Full workout execution, calendar
sync, gamification and optional location synchronization remain outside this gate.
