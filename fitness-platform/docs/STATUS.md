# Project status

Status basis: 2026-07-13, branch `codex/fix-scaffold-reproducibility`, verified Gate Q
checkpoint `ed34e4f` plus the locally verified Phase 2A.1 training-location slice.

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
- Repository, migration, static-analysis, JVM/build and container verification workflows.

## Schema and verification authority

- Alembic head: `0d4f6a8b2c17`.
- Android Room version: 6; exported schemas 1–6 are committed.
- The canonical command matrix and connected-test limitation are in [TESTING.md](../TESTING.md).
  Phase 2A.1 passed the complete 128-test PostgreSQL backend suite on pytest 9.1.1 at
  79.27% combined coverage, fresh SQLite/PostgreSQL migration checks, 43 distinct Android JVM
  tests (81 debug/release executions), the 579-task Android gate and the 192-task AndroidTest
  compile gate.
- Latest verified debug APK: 40,585,190 bytes; SHA-256
  `fa17cc9964a21df59eeb82cb477e1ead483f9c87834a3304be53dd10f2a68c59`.
- Gate Q CI [run 29253072791](https://github.com/Saltybukket/Momentum/actions/runs/29253072791),
  attempt 1, passed all backend, Android and repository-security jobs for `ed34e4f`.
- Phase 2A.1 implementation CI [run 29256173819](https://github.com/Saltybukket/Momentum/actions/runs/29256173819),
  attempt 1, passed all three jobs for `745894a` without reruns.

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

App Shell V1 and local Phase 2A.1 training locations are complete. The next product slice is Phase
2B (`ExerciseReference` and editable training plans); optional location synchronization remains a
separate explicitly gated slice and is not a prerequisite for local use.

Latest operational handoff: [Phase 2A.1 training locations](HANDOFF_2026-07-13_PHASE_2A1_TRAINING_LOCATIONS.md). Historical
reports and handoffs are under [archive](archive/README.md).
