# Premium UI checkpoint handoff

## Checkpoint

- Date: 2026-07-13
- Branch: `codex/fix-scaffold-reproducibility`
- UX.0 commits: `8410a51` and corrective schema-artifact cleanup `9b9325e`
- UX.0 CI: [run 29261976736](https://github.com/Saltybukket/Momentum/actions/runs/29261976736),
  attempt 1, success for `9b9325e`; backend, Android and repository-security passed.
- Premium implementation: `07aa0bf5067c1575623705042d3b834b2b1b3e2a`, subject
  `feat(android): deliver Momentum premium UI foundation`, pushed to origin.
- Premium implementation CI: [run 29265287902](https://github.com/Saltybukket/Momentum/actions/runs/29265287902),
  attempt 1, success for exact head `07aa0bf5067c1575623705042d3b834b2b1b3e2a` without rerun or
  cancellation. Backend passed in 1m30s, Android in 7m11s and repository-security in 50s. The
  documentation checkpoint is the commit containing this handoff.

## Delivered contracts

- Unsupported Room downgrade fails closed without deleting data. The startup probe emits only a
  generic recovery instruction without raw exceptions, paths or secrets.
- Location names use the shared bounded single-line policy; location mutations expose finite busy,
  success and controlled error states. Active switching updates former and new rows atomically.
- Catalog detail explicitly distinguishes compatible, location-required and missing-equipment
  states and uses registry-backed localized equipment labels.
- `android/core/designsystem` owns Momentum light/dark color, typography, spacing, shape,
  elevation, motion and responsive component tokens.
- The compact/expanded four-root shell and all existing product surfaces received a consistent
  premium pass without introducing training-plan, progress, reward or other future-domain state.
- Launcher and splash use self-authored Android vectors in deep navy, soft silver and muted amber.
  Adaptive, round and monochrome contracts are declared. Backup/data extraction remains disabled.
- `icon_and_image_ideas/` remains ignored reference material and is absent from the APK and tracked
  source archive.

## Verification

- Backend Ruff format/check and mypy passed. The dedicated PostgreSQL suite passed `128` tests,
  zero skips, with `79.14%` combined statement/branch coverage.
- Fresh PostgreSQL upgrade/check passed with exactly one Alembic head: `0d4f6a8b2c17`.
- Android Spotless, Detekt, JVM tests, Lint and debug assembly passed in `644` Gradle tasks:
  `49` distinct JVM tests, `92` debug/release executions, zero failures/skips.
- Release-facing Lint findings moved from MissingApplicationIcon 1, DataExtractionRules 1,
  PluralsCandidate 8 and UseKtx 3 to zero in all four classes. The remaining 29 app-report warnings
  are dependency/version notices plus the required adaptive-icon qualifier notice; versions were
  not changed blindly in this UI slice.
- The five AndroidTest compilation targets passed in `208` tasks. Connected tests did not run.
- Room remains version `6`; exported schemas 1–6 are unchanged.
- Debug APK: `41,791,719` bytes; SHA-256
  `391b7c8bb5c35db9ad7a51de666628bb86365f71d7ec0237262a14d7159bbe0b`.
  Archive inspection found no `icon_and_image_ideas` entry.
- OpenAPI had no drift. Compose config/build/up, container migration and the rerun smoke flow after
  confirmed healthy startup passed. The first smoke attempt raced the recreated container while
  its health state was still `starting` and was not counted as a product failure.
- Repository integrity and `git diff --check` passed. Gitleaks 8.30.0 scanned 50 commits and found
  no leak. Trivy 0.66.0 found zero fixable HIGH/CRITICAL filesystem or runtime-image findings; the
  runtime image currently reports 20 unfixed HIGH/CRITICAL Debian findings.

## Remaining external limitation and next scope

Connected visual/accessibility execution is not claimed because no stable Windows API-36 Google
APIs x86_64 AVD is available. Install it with:

```text
sdkmanager.bat "system-images;android-36;google_apis;x86_64"
avdmanager.bat create avd -n Momentum_API_36 -k "system-images;android-36;google_apis;x86_64"
```

Phase 2A.2 location sync and Phase 2B were deliberately not started. The next product task is
Phase 2B: `ExerciseReference` and editable training plans with days, blocks, ordered exercises,
sets/reps/RPE/RIR/rest/tempo.
