# Handoff: Gate D.1 / E Android sync hardening

Date: 2026-07-13  
Branch: `codex/fix-scaffold-reproducibility`

## Completed contracts

- Gate D.1 is commit `0583fdb`: token-bound just-in-time outbox claims, renewable async leases, lost-claim reporting and explicit handler/no-op disposition.
- Alembic head is `f26c8d0e531a`. Its upgrade now also recovers safely from an interrupted environment where the claim-token column exists but the index/version checkpoint does not.
- Room schema 5 adds singleton `sync_state`. Schema 4→5 resets the exercise cursor to 0; page changes and `nextCursor` commit together; the legacy DataStore cursor is removed only after a Room checkpoint.
- Guest token and recovery secret use random-IV AES/GCM under Android Keystore. Plaintext preferences are read, encrypted, verified and deleted repeatably. Invalidated credentials rotate installation identity without deleting Room data. Bootstrap is mutex-protected.
- Consent defaults false. Opt-in persists then enqueues unique work; opt-out persists then cancels. The worker rechecks consent before auth, push and each pull page. Catalog seed/refresh remains public and independent.
- Worker errors persist only bounded classifications. Catalog and Privacy UI expose finite loading, cached-offline, error, not-found and consent-change states; visible copy is resource-backed.

## Verification

- Backend Ruff format/check and mypy: passed.
- Backend full suite with isolated PostgreSQL: **95 passed**. Combined coverage **79.03%**; statement coverage **83.23%** (1,990/2,391); branch coverage **51.11%** (184/360).
- Fresh SQLite upgrade/check: passed; fresh PostgreSQL tests and production-container upgrade/check: passed. Head: `f26c8d0e531a`.
- Android `spotlessCheck detekt test lintDebug assembleDebug`: passed.
- Android JVM tests: 9 unique tests across 5 suites, passed for debug and release variants.
- AndroidTest source compilation for `core:database`, `core:sync`, `data` and `app`: passed.
- Connected execution: not run; external blocker remains the absent stable Windows API-36 system image/AVD.
- Docker Compose config/build/up, healthy services, container Alembic check and smoke flow: passed.
- OpenAPI export has no drift. Repository integrity and `git diff --check`: passed.
- Gitleaks 8.28.0: 30 commits / about 3.83 MB scanned, no leaks.
- Trivy 0.66.0: gate failed on 20 Debian 13 base-image HIGH/CRITICAL findings, including deferred/unfixed Perl and util-linux-family advisories. No Python-package vulnerability was reported. This external base-image remediation remains open; do not misreport the scan as green.
- Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`, 40,240,659 bytes, SHA-256 `09ebda887ea8a3e36194f4b71d9f296f5f6645f4cbe974bd4456566d0808544c`.
- GitHub Actions run `29213415409` for commit `a3761c5193cdff8fc01612284a449725c5f7749c` passed: backend 1m14s, Android 6m04s and repository/security 44s. URL: <https://github.com/Saltybukket/Momentum/actions/runs/29213415409>.

## Next boundary

Do not start Gate F/G/H implicitly. The next product slice named by the task is immutable, atomic and retractable catalog releases and needs a separate prompt. First inspect the final GitHub Actions run from this head and the Trivy base-image findings. Connected tests remain conditional on the Windows API-36 AVD.
