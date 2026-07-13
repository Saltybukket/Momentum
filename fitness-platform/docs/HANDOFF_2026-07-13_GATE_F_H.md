# Gate F and H-lite handoff

Gate F functional checkpoint: `2b2f4fe` on
`codex/fix-scaffold-reproducibility`. Gate H-lite is the documentation/container/source-archive
checkpoint immediately following it.

## Delivered

- Immutable release-scoped public catalog storage, full validation/staging, atomic activation,
  older-release retention and explicit rollback.
- Public API pages/snapshots bound to one active version/hash and Android canonical hash validation
  before atomic Room replacement.
- Canonical `docs/STATUS.md`; historical implementation report and older handoffs archived.
- Deterministic tracked-source ZIP through `make source-archive` with safe sorted paths and SHA-256.
- Digest-pinned multi-stage non-root backend runtime without tests/dev tools, plus separate CI
  filesystem and loaded-image Trivy gates/reports.

## Verified

- Backend: 105 PostgreSQL-backed tests, 78.83% combined statement/branch coverage; Ruff and strict
  mypy green.
- Alembic: head `0d4f6a8b2c17`; fresh SQLite and PostgreSQL upgrade/check/downgrade/re-upgrade green.
- Android: Spotless, Detekt, JVM tests, Lint, debug assembly and relevant AndroidTest compilation
  green. Connected execution remains unavailable without the stable Windows API-36 AVD.
- Demo catalog: 3 CC0 records activated; identical reimport reports 3 unchanged and no activation.
- Docker Compose rebuild, container migration and HTTP smoke flow green.
- Trivy 0.66.0: zero fixable HIGH/CRITICAL; 20 unfixed/deferred Debian 13.5 findings remain visible.

## Next

App Shell V1 is the only permitted next slice from the current task and should begin only with
sufficient remaining budget. Otherwise stop at this clean checkpoint. Do not open TrainingLocation,
planning, privacy or production-auth domains implicitly.
