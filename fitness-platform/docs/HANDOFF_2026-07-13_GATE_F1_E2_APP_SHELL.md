# Gate F.1, Gate E.2 and App Shell V1 handoff

Date: 2026-07-13  
Branch: `codex/fix-scaffold-reproducibility`  
Implementation checkpoint: `6e2e5bcfec80921bc457758bf985bdc93a425720`
Final documentation checkpoint: `8dfc6bcb2de3f2f6cfe8e4c21acda4d05d1b85ed`

## Delivered contracts

- Public exercise data is staged in immutable releases, canonically hashed, validated and
  atomically activated. The exact backend/API/Android demo snapshot uses release
  `2026.07.13-demo.2`, batch `momentum-self-authored-demo-v2` and SHA-256
  `b3a3fec064548ccdcf1c9166d5641f1b8c9da46a19337feeb88ae923906f1fa7`.
- The self-authored CC0 demo contains three public catalog exercises. Public endpoints are
  `GET /api/v1/catalog/exercises`, `/api/v1/catalog/exercises/{id}`,
  `/api/v1/catalog/snapshot`, `/api/v1/catalog/muscles` and `/api/v1/catalog/equipment`.
- Android keeps catalog, private exercises and sync state in Room as source of truth. Room remains
  at version 5 with schemas 1–5 committed.
- Rejected or invalid guest credentials have an explicit visible recovery/reset path that
  preserves local data. Outbox success, failure and conflict finalization requires the active
  claim owner and rejects stale workers.
- App Shell V1 provides Home, Workouts, Exercises and Profile roots, adaptive compact/expanded
  navigation, Material 3 light/dark styling and a dashboard backed only by real local state.

## Verification evidence

- Backend formatting and analysis: Ruff format/check and mypy passed.
- Backend: 127 passed, 0 failed, 79.40% combined coverage against a dedicated PostgreSQL database.
- Alembic: fresh SQLite and PostgreSQL upgrades passed; `alembic check` reported no operations;
  sole head is `0d4f6a8b2c17`.
- Android emulator-independent matrix passed: Spotless, Detekt, JVM tests, Lint, debug assembly and
  instrumentation-source compilation; Gradle completed 624 tasks successfully.
- Debug APK: 40,767,038 bytes; SHA-256
  `c8790cf556c773caafc3445169a73d549770e91c66431e632820ab85ae899a14`.
- Docker Compose configuration passed; PostgreSQL, Redis and backend were healthy; container
  migration and the HTTP smoke flow passed.
- Repository policy check passed. Local Docker Gitleaks scanned 44 commits with no leak. The
  implementation-checkpoint source archive contained 238 files and SHA-256
  `c2c709453987446666e00b8c9b8eef70da11158e3ebf7b4b890dce7206e34893`. The reproducible archive
  generated after final checkpoint `8dfc6bc` contained 239 files and SHA-256
  `92dc9006588b24ae0d569875768050f647071c423542e3597ba3df25f0ab5a9a`.
- GitHub Actions [run 29244974289](https://github.com/Saltybukket/Momentum/actions/runs/29244974289)
  passed backend, Android and repository-security jobs for the implementation checkpoint. The
  security job passed repository policy, Gitleaks, filesystem Trivy and runtime-image Trivy policy.
- Final repetition [run 29245915692](https://github.com/Saltybukket/Momentum/actions/runs/29245915692)
  passed the same three jobs for final head `8dfc6bcb2de3f2f6cfe8e4c21acda4d05d1b85ed`.

## External blocker

Connected Android/UI acceptance execution remains unclaimed because no stable Windows API-36
system image/AVD is available. AndroidTest sources compile in both local and CI gates. This blocker
does not affect JVM tests, Room code, Android compilation, backend verification or migrations; use
the exact Windows setup and runner procedure in `TESTING.md` once the image is available.

## Commit sequence

- `eb94db2` `docs: strengthen canonical hash and test rules`
- `b51ff49` `fix(catalog): make release snapshots canonically verifiable`
- `27037a5` `fix(android): expose credential recovery and bind sync finalization`
- `53d8ec2` `docs: align catalog and android recovery contracts`
- `6e2e5bc` `feat(android): introduce the Momentum app shell`
- `8dfc6bc` `docs: record verified app shell checkpoint`

All listed commits are pushed to `origin/codex/fix-scaffold-reproducibility`.
