# Project status

Status basis: 2026-07-13, branch `codex/fix-scaffold-reproducibility`, verified Gate-F.1/E.2
and App Shell V1 checkpoint `6e2e5bc`.

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
- Repository, migration, static-analysis, JVM/build and container verification workflows.

## Schema and verification authority

- Alembic head: `0d4f6a8b2c17`.
- Android Room version: 5; exported schemas 1–5 are committed.
- The canonical command matrix and connected-test limitation are in [TESTING.md](../TESTING.md).
  Gate F.1/App Shell passed the complete 127-test PostgreSQL backend suite at 79.40% combined coverage,
  fresh SQLite/PostgreSQL migration round trips and the full emulator-independent Android gate.
- Latest verified debug APK: 40,767,038 bytes; SHA-256
  `c8790cf556c773caafc3445169a73d549770e91c66431e632820ab85ae899a14`.
- App Shell implementation CI: [run 29244974289](https://github.com/Saltybukket/Momentum/actions/runs/29244974289),
  all backend, Android and repository-security jobs passed for `6e2e5bc`.

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

App Shell V1 is complete. Training-location and planning domains remain later Phase-2 vertical
slices; no new domain should be opened merely for navigation placeholders.

Latest operational handoff: [Gate F.1/E.2/App Shell checkpoint](HANDOFF_2026-07-13_GATE_F1_E2_APP_SHELL.md). Historical
reports and handoffs are under [archive](archive/README.md).
