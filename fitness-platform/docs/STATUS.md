# Project status

Status basis: 2026-07-13, branch `codex/fix-scaffold-reproducibility`, verified Gate-F
checkpoint `2b2f4fe`.

## Implemented

- Offline-first Android guest profile, private custom exercises and minimal workout lifecycle.
- Owner-scoped private-exercise push/pull sync with tombstones, durable conflicts, Room cursor
  checkpointing, token renewal and explicit consent cancellation.
- FastAPI modular monolith with development guest recovery, idempotency, reliable leased outbox,
  private exercise/workout APIs and PostgreSQL persistence.
- Immutable authoritative public catalog releases with validated import, atomic activation,
  retained rollback history, public page/detail/facet/snapshot APIs and Android Room cache.
- Repository, migration, static-analysis, JVM/build and container verification workflows.

## Schema and verification authority

- Alembic head: `0d4f6a8b2c17`.
- Android Room version: 5; exported schemas 1–5 are committed.
- The canonical command matrix and connected-test limitation are in [TESTING.md](../TESTING.md).
  Gate F passed the complete 105-test PostgreSQL backend suite, fresh SQLite/PostgreSQL migration
  round trips and the full emulator-independent Android gate.

## Active risks

- Guest identity remains a development contract, not production authentication or account linking.
- Stable Windows API-36 system image/AVD is unavailable, so connected acceptance tests are not
  claimed; AndroidTest sources compile.
- Health, nutrition, rewards, social, commerce, AI and production provider integrations are not
  implemented.
- CI records unfixed HIGH/CRITICAL Trivy findings as artifacts; only findings with an available
  fix fail the gate. No vulnerability exception is silently ignored.

## Next phase

After documentation/security CI stabilization, the next bounded product step is Momentum App
Shell V1. Training-location and planning domains follow as later Phase-2 vertical slices; no new
domain should be opened merely for navigation placeholders.

Latest operational handoff: [Gate F/H-lite checkpoint](HANDOFF_2026-07-13_GATE_F_H.md). Historical
reports and handoffs are under [archive](archive/README.md).
