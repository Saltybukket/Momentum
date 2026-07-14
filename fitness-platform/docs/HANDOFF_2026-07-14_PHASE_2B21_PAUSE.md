# Handoff: Phase 2B.2.1 Calendar Integrity & Productization

Date: 2026-07-14

Branch: `codex/fix-scaffold-reproducibility`

Repository: `/home/student/projects/Momentum` in WSL2 `ISP2025`

## Resume instruction

Continue from this file and the root `AGENTS.md`. The user only needs to say **“mach weiter”**.

Do not restart the calendar analysis or add new product scope. Resume at **Remaining work**, first verifying the pending CI run.

## Safe checkpoint

The implementation is committed and pushed through:

```text
1b76d67aadff09613d4c14d7628a093eb0b2d3d9 build: make source archives commit reproducible
a3d1aa786acbc03c67bd41f660a0d9fa881dad4e feat(android): productize calendar scheduling and conflicts
80cce90e35ed1ba8119791dd091e4a6a3db98238 fix(android): preserve calendar integrity across plan changes
```

Remote branch is current through `1b76d67`.

CI evidence:

- `80cce90`: run `29298490085`, attempt 1, success.
- `a3d1aa7`: run `29300839146`, attempt 1, success; backend, Android and repository/image security green.
- `1b76d67`: run `29301504906`, attempt 1, success; exact head SHA verified.

Run URL pattern: `https://github.com/Saltybukket/Momentum/actions/runs/<run-id>`.

## Implemented

### Calendar integrity / Room 9

- ADR-019 and explicit Room `8 -> 9` migration with exported schema.
- Plan-day FK protection, durable occurrence snapshot identity and canonical equipment sets.
- Differential plan aggregate updates preserve schedules and occurrence history.
- Explicit blocked-removal behavior and rollback tests.
- Archive/delete preserve history and explicitly deactivate affected schedules.

### Scheduling and conflict behavior

- Deterministic `1/2/4/12`-week cycle formula.
- Rolling 56-day horizon with idempotent gap recovery.
- Occurrence-scoped equipment/unavailable snapshots; no global active-plan authority leak.
- Owner-scoped availability, overrides and overlap detection.
- All overlap participants are reported, including nested, chained and cross-midnight intervals.
- DST gaps are invalid; DST overlaps use the earlier valid offset deterministically.
- Moved, copied, ad-hoc, completed and terminal rows survive horizon repair/future replacement.
- Permanent schedule changes require preview then confirmation.
- Central safe text and status/action policies.

### Product UI

- Real Today, seven-day Week, `YearMonth` Month and chronological Agenda views.
- Today hero for the next workout; time, duration, named location, status and conflicts shown.
- Named/localized location selector, no UUID input and no raw enum values.
- Availability prefill, overview, override visibility and reset/delete behavior.
- Explicit schedule setup for every plan day, IANA time-zone validation and no silent `18:00` default.
- Non-persisting 56-day conflict preview before explicit schedule materialization.
- Explicit active-plan/schedule drift decision: keep schedule, start new schedule setup or cancel.
- Terminal/status-specific actions; editors remain open until persistence success.
- Compact light, expanded dark and 200% font Compose previews.
- Duration/future-count strings use plural resources; the reported `PluralsCandidate` was removed.

### Commit-reproducible source archive

- Archive paths, bytes and Unix execute bits are read from Git tree/blob objects for the selected commit, never from the working tree.
- Deterministic manifest records repository, commit SHA, file count and generator version.
- Regression test changes tracked bytes and removes an execute bit without changing the archive hash.
- Implementation-head archive for `1b76d67`:

```text
files=305 (including SOURCE_ARCHIVE_MANIFEST.json)
sha256=684497e15856774fbff10a6630ab89b09be6a87e8ef684847571bdab04cc3f01
```

This is the implementation-head archive, not the future final documentation-head archive.

## Verified locally

Android:

```text
./gradlew spotlessCheck detekt test lintDebug assembleDebug --no-daemon
BUILD SUCCESSFUL; 656 actionable tasks

AndroidTest source compilation matrix
BUILD SUCCESSFUL; 208 actionable tasks
```

Backend with real PostgreSQL `fitness_test`:

```text
Ruff format/check: passed
mypy: passed (43 source files)
pytest: 129 passed in 208.31s
combined statement/branch coverage: 79.17%
```

Focused source archive regression: `1 passed`.

Baseline schema facts retained from the beginning of this run:

```text
Alembic head: 0d4f6a8b2c17
Room version: 9
```

## Remaining work

1. Update only affected canonical documentation: root/project README as needed, `ARCHITECTURE.md`, `DATA_MODEL.md`, `TESTING.md`, `CHANGELOG.md`, `docs/STATUS.md`, and this newest handoff. Do not append to historical `IMPLEMENTATION_REPORT.md`.
2. Correct old archive claims: distinguish the `1b76d67` implementation-head archive above from the final documentation-head archive. Do not claim the old 300-file hash.
3. Run the final emulator-independent matrix:
   - backend Ruff, mypy and 129+ PostgreSQL tests;
   - Alembic heads/check plus fresh SQLite and separate PostgreSQL migration databases;
   - OpenAPI export/drift;
   - full Android 656-task matrix and 208-task AndroidTest compilation matrix;
   - Docker Compose config/build/up/ps, container migration and smoke test;
   - repository check, `git diff --check`, Gitleaks, Trivy filesystem and built-runtime-image scans;
   - APK SHA-256 and package-content inspection;
   - source archive generated twice for the final documentation commit.
4. Commit documentation/final evidence as a separate Conventional Commit, push it, and wait for the exact final CI run to complete successfully.
5. Regenerate and report the final documentation-head archive count/hash after that commit. Do not place the archive hash inside the archive manifest.
6. Finish with an empty `git status --short` and exact run IDs, URLs, attempts and head SHAs.

## External blocker

The missing stable Windows API-36 system image/AVD blocks only connected/UI acceptance execution. AndroidTest sources compile. Do not let this block JVM tests, Room code, builds, documentation or commits. Run connected tests only if a valid device/AVD is actually available, and never report compilation as execution.
