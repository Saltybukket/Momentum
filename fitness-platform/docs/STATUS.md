# Project status

Status basis: 2026-07-13, branch `codex/fix-scaffold-reproducibility`, verified Gate UI.1
checkpoint `62945a7` plus locally verified Phase 2B.1 training plans.

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
- Repository, migration, static-analysis, JVM/build and container verification workflows.

## Schema and verification authority

- Alembic head: `0d4f6a8b2c17`.
- Android Room version: 7; exported schemas 1–7 are committed.
- The canonical command matrix and connected-test limitation are in [TESTING.md](../TESTING.md).
  Phase 2A.1 passed the complete 128-test PostgreSQL backend suite on pytest 9.1.1 at
  79.27% combined coverage, fresh SQLite/PostgreSQL migration checks, 43 distinct Android JVM
  tests (81 debug/release executions), the 579-task Android gate and the 192-task AndroidTest
  compile gate.
- The current APK size/hash and final test counts are recorded in the latest premium UI handoff.
- Premium local verification passed 128 PostgreSQL backend tests at 79.14% combined coverage,
  49 distinct Android JVM tests (92 variant executions), the 644-task Android gate and 208-task
  AndroidTest compile gate. Room remains version 6.
- Gate UI.1 local verification passed 128 PostgreSQL backend tests at 79.20% combined coverage,
  56 distinct Android JVM tests (106 debug/release and pure-JVM executions), the 656-task Android
  gate and 208-task AndroidTest compile gate. Room remains version 6.
- Phase 2B.1 local verification passed 128 PostgreSQL backend tests at 79.37% combined coverage,
  fresh PostgreSQL and SQLite Alembic upgrade/check at `0d4f6a8b2c17`, Android JVM/domain tests,
  the complete Android lint/static-analysis/build matrix and compilation of Room/data AndroidTest
  sources. Connected Room migration execution is not claimed because the stable API-36 AVD remains
  unavailable. Room is version 7.
- Gate Q CI [run 29253072791](https://github.com/Saltybukket/Momentum/actions/runs/29253072791),
  attempt 1, passed all backend, Android and repository-security jobs for `ed34e4f`.
- Phase 2A.1 implementation CI [run 29256173819](https://github.com/Saltybukket/Momentum/actions/runs/29256173819),
  attempt 1, passed all three jobs for `745894a` without reruns.
- UX.0 correction CI [run 29261976736](https://github.com/Saltybukket/Momentum/actions/runs/29261976736),
  attempt 1, passed all three jobs for `9b9325e` without reruns.
- Premium implementation CI [run 29265287902](https://github.com/Saltybukket/Momentum/actions/runs/29265287902),
  attempt 1, passed backend, Android and repository-security for exact head `07aa0bf` without rerun.
- Phase 2B.1 CI [run 29290254292](https://github.com/Saltybukket/Momentum/actions/runs/29290254292),
  attempt 1, passed Android, backend and repository-security for exact implementation head
  `5f6fc73` without reruns.

## Active risks

- Guest identity and explicit replacement remain a development contract, not production
  authentication, revocation or account linking. Replacing local credentials does not delete
  previously stored server data.
- Stable Windows API-36 system image/AVD is unavailable, so connected acceptance tests are not
  claimed; AndroidTest sources compile.
- Health, nutrition, rewards, social, commerce, AI and production provider integrations are not
  implemented.
- CI records unfixed HIGH/CRITICAL Trivy findings as artifacts; only findings with an available
  fix fail the gate. No vulnerability exception is silently ignored.

## Next phase

Phase 2B.1 editable offline training plans are locally complete. The next explicitly gated slice is
Phase 2B.2, the local training calendar and occurrence materialization. Optional location
synchronization remains separate and is not a prerequisite for local use.

Latest operational handoff: [Phase 2B.1 checkpoint](HANDOFF_2026-07-13_PHASE_2B1.md). Historical
reports and handoffs are under [archive](archive/README.md).
