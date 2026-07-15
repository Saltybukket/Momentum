# Gate 18D completion handoff: Workout History and Repeat stabilization

Date: 2026-07-15

Branch: `codex/fix-scaffold-reproducibility`

Implementation commit: `4993310ac60a7c473ec396f3811a0599b2485908`

## Scope completed

Gate 18D accepted the existing Phase 2B.2.1 calendar cleanup and stabilized only the already
started Workout History/detail/Repeat slice. Repeat creates one new `PLANNED` workout from a
completed owner-scoped source, preserves trimmed notes and ordered exercise references, and emits
the normal create outbox record. It is not workout execution and does not add sync, rewards,
analytics or immutable execution snapshots.

Workout detail resolves exercise names from current owner-visible Exercise data. If any reference
is missing, the UI marks it unavailable and disables Repeat with a localized explanation. Loading
and not-found states remain visible and navigable rather than auto-popping.

## Audit resolution

| Finding | Resolution |
| --- | --- |
| F18C-01 inaccurate test report | Replaced claims with measured JVM counts and explicit compile-versus-execution evidence. |
| F18C-02 post-Repeat navigation race | Added a just-created durable-result bridge; navigation waits for successful repository creation. |
| F18C-03 duplicate/concurrent operations | Added synchronous operation reservation, busy disabling and `finally` cleanup. |
| F18C-04 untested Repeat contract | Added dedicated domain success, rejection and propagation tests. |
| F18C-05 untested data lifecycle | Added eight real in-memory Room repository AndroidTests. |
| F18C-06 non-atomic create validation | Validation, aggregate writes and outbox recording now run in one Room transaction. |
| F18C-07 Repeat with missing Exercise | Eligibility requires every ordered reference to resolve. |
| F18C-08 incomplete detail UI | Added loading/not-found/content states, localized time, accessibility semantics and five previews. |
| F18C-09 unscoped Workout reads | DAO and repository get/observe/lifecycle boundaries require owner identity. |
| F18C-10 stale Android gate evidence | Re-ran the full local Android gate and exact-head GitHub Actions CI. |

Calendar legacy use cases remain removed, the typed calendar exception and coordinator guards stay
in place, and no calendar behavior was broadened.

## Verification evidence

- `./gradlew spotlessApply :domain:test :feature:main:testDebugUnitTest :data:compileDebugAndroidTestKotlin --no-daemon` — passed.
- `./gradlew spotlessApply detekt --no-daemon` — passed, 7 actionable tasks.
- `./gradlew spotlessCheck detekt test lintDebug assembleDebug --no-daemon` — passed in 2m14s,
  656 actionable tasks (114 executed, 542 up-to-date).
- JUnit XML: 91 distinct JVM tests, 156 task/variant executions, zero failures, errors or skips
  across 37 suite reports.
- `./gradlew :core:database:compileDebugAndroidTestKotlin :core:datastore:compileDebugAndroidTestKotlin :core:sync:compileDebugAndroidTestKotlin :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin --no-daemon` — passed in 28s, 208 actionable tasks (1 executed, 207 up-to-date).
- AndroidTest sources contain 67 test methods; eight belong to the new real
  `RoomWorkoutRepositoryTest`. They compiled but were not executed on a device.
- `python3 fitness-platform/scripts/check_repository.py` and `git diff --check` — passed.
- `uv run alembic heads` — `0d4f6a8b2c17 (head)`; no backend or migration change.
- Room remains version 9 with committed schemas 1–9; no Room schema change.
- Debug APK SHA-256: `01b791cbbbbac233d960be1af0aa3795a0013109600d1b9847981bfb3d86f3ac`
  (42,925,966 bytes).
- GitHub Actions [run 29406924215](https://github.com/Saltybukket/Momentum/actions/runs/29406924215),
  attempt 1, passed Android, backend and repository-security without reruns for exact head
  `4993310ac60a7c473ec396f3811a0599b2485908`.

## External blocker and remaining boundaries

`make doctor-connected` still reports only the missing stable Windows API-36 Google APIs x86_64
system image. Install it with:

```text
sdkmanager.bat "system-images;android-36;google_apis;x86_64"
```

Connected tests are therefore compiled but not claimed as executed. Full Workout Execution remains
closed. Current exercise resolution is deliberately not an immutable execution snapshot. Owner
scoping is verified for the Workout boundary only; other private repositories need a later,
separately authorized identity audit.
