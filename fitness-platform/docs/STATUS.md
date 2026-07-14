# Project status

Status basis: 2026-07-15, branch `codex/fix-scaffold-reproducibility`, verified Phase 2B.2.1
implementation commit `9550f9ccc2aa8f9ba58ce165307fa48633d50c1d`.

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
- Repository, migration, static-analysis, JVM/build and container verification workflows.

## Schema and verification authority

- Alembic head: `0d4f6a8b2c17`.
- Android Room version: 9; exported schemas 1–9 are committed.
- The canonical command matrix and connected-test limitation are in [TESTING.md](../TESTING.md).
- Phase 2B.2.1 local verification passed 129 PostgreSQL backend tests with zero skips and 79.20%
  combined statement/branch coverage, fresh PostgreSQL and SQLite Alembic upgrade/check at
  `0d4f6a8b2c17`, 78 distinct Android JVM tests (134 task/variant executions), the complete Android
  gate and compilation of all changed AndroidTest source modules. The final combined Gradle command
  reported 701 actionable tasks (120 executed, 581 up-to-date). Connected Room migration execution
  is not claimed because the stable API-36 AVD remains unavailable.
- Docker Compose config/build/up, container migration and smoke passed. Gitleaks found no secrets;
  Trivy found no fixed HIGH/CRITICAL filesystem or runtime-image vulnerabilities. The image report
  retains 20 unfixed HIGH/CRITICAL Debian findings for review.
- Phase 2B.2.1 implementation CI
  [run 29347954640](https://github.com/Saltybukket/Momentum/actions/runs/29347954640), attempt 1,
  passed Android, backend and repository-security for exact head `9550f9c` without reruns. Full
  local evidence is in [the Phase 2B.2.1 completion handoff](HANDOFF_2026-07-15_PHASE_2B21_COMPLETE.md).

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

Phase 2B.2.1 is complete. No later feature gate is open. Workout execution, calendar sync,
gamification, optional location synchronization and the visual design sprint remain outside this
gate. Removing a plan day referenced by dated calendar state is deliberately unsupported rather
than destructive; a future flow requires explicit impact preview and confirmation.
