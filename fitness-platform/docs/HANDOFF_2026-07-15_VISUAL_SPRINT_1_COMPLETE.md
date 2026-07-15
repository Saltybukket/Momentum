# Visual Sprint 1 / Gate 20C completion handoff

Date: 2026-07-15

Branch: `codex/fix-scaffold-reproducibility`

Locally accepted code head: `9321e83`

Baseline upstream before this run: `da1c62e`

## Outcome

Gate 20C Visual Sprint 1 is locally complete. Windows AVD `Pixel_10` ran as the sole device
`emulator-5554` from `android-36.1/google_apis_playstore/x86_64`. Runtime properties prove a
stable API-36 Android 16 build: `preview_sdk=0`, codename `REL`, release-key fingerprint. No Visual
Sprint 2, workout-execution, sync, gamification or other later-gate scope was started.

The connected runner now selects all five default modules correctly, records per-module raw logs,
and validates exact completion through a tested parser. App acceptance verifies real root/nested
screen transitions, route-family selection, Navigate-Up, system Back and unmerged icon content
descriptions. Feature acceptance verifies conflict confirmation/mutation ordering, responsive
tracking controls, status semantics and workout-detail states. Espresso resolves to 3.7.0 in both
affected AndroidTest runtime graphs.

## Verified matrix

- Stable API-36 Connected: 87/87 — database 20, datastore 8, data 36, feature 14, app 9.
- Connected aggregate raw log:
  `android/build/connected-test-results/20260715T162301Z.txt`.
- Android JVM: 101 distinct tests, 176 debug/release task executions, zero failures/errors/skips.
- Android gate: `spotlessCheck detekt test lintDebug assembleDebug` — 656 actionable tasks;
  48 executed, 4 from cache, 604 up-to-date.
- AndroidTest compilation: database, datastore, sync, data, feature and app passed; sync is
  intentionally `NO-SOURCE`.
- Backend: 129 PostgreSQL-backed tests passed in 175.25 seconds; combined coverage 79.24%.
- Ruff format/check and mypy passed.
- Alembic PostgreSQL and fresh SQLite upgrade/check passed; single head `0d4f6a8b2c17`.
- Room version 9; exported schemas 1–9 remain committed.
- OpenAPI export has no drift.
- Docker Compose config/build/up and health-gated smoke passed. The first post-recreate smoke
  request hit `health: starting`; the required retry after `docker compose up -d --wait` passed.
- Repository integrity and `git diff --check` passed.
- Gitleaks v8.30.0 scanned 84 commits and found no leaks.
- Trivy 0.66.0 found zero fixed HIGH/CRITICAL findings in the filesystem and built runtime image;
  the full image report contains 22 unfixed/deferred Debian HIGH/CRITICAL findings.
- Debug APK SHA-256:
  `691899dae698967e414c57c160d3dd9d1030d5fbe65d74f6c7c637383122d828`.
- APK inspection confirms `icon_and_image_ideas/` is absent.

## Commits added during API-36 acceptance

- `182ae80 test(android): harden Gate 20C acceptance`
- `7cd3571 fix(android): correct connected runner defaults`
- `f5e2ec0 test(android): stabilize API 36 acceptance flows`
- `9321e83 style(scripts): format connected result parser`

The preceding local Visual Sprint 1 commits remain unchanged; see Git history and the pause
handoff for their complete list.

## Remaining acceptance step and risks

Commit and push this completion documentation, then require a green GitHub Actions run whose head
SHA exactly matches the final pushed commit. Record run ID, URL, attempt and head SHA before
starting a later gate.

Visual Sprint 2 is permitted only after that exact final CI run is green and the user explicitly
authorizes the next gate.

The 22 unfixed/deferred runtime-image findings remain a tracked base-image risk. Production
identity, full workout execution, calendar sync, rewards, social, commerce and provider adapters
remain outside Gate 20C.

`make doctor` remains non-blocking and warns because it looks specifically for
`android-36/google_apis/x86_64`; `make doctor-connected` therefore does not recognize the accepted
stable `android-36.1/google_apis_playstore/x86_64` image even though the complete runtime matrix
passed on it. Per the active scope, the Doctor was not broadened during this acceptance gate.
