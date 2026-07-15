# Gate 20C pause handoff

Date: 2026-07-15  
Branch: `codex/fix-scaffold-reproducibility`  
Local candidate: `d0dfcf6`  
Upstream before this run: `da1c62e`

## Outcome

Visual Sprint 1 code and acceptance coverage are locally complete, but Gate 20C is not accepted and
must not be pushed yet. The required stable Windows API-36 Google APIs/x86_64 system image and AVD
are absent. The available Pixel_10 emulator is API 37 / Android 17 preview and is diagnostic only.

Local commits added in this run:

- `2da8e4a fix(android-ui): harden visual interaction contracts`
- `d0dfcf6 test(android): strengthen visual acceptance coverage`

The four inherited DeepSeek commits remain unchanged below them: `858f9b3`, `b0dfcfe`, `e36a51b`
and `c3b3c39`.

## Verified evidence

- Backend: Ruff format/check and mypy passed; real PostgreSQL pytest passed 129 tests with zero
  skips and 79.17% combined statement/branch coverage.
- Alembic: single head `0d4f6a8b2c17`; upgrade and check passed on the dedicated migration DB.
- Android JVM/static/build: 101 distinct tests, 176 task/variant executions, zero failures/errors/
  skips. `spotlessCheck detekt test lintDebug assembleDebug` passed (656 tasks: 62 executed, 594
  up-to-date). Five AndroidTest source sets compile; the sync task succeeds with `NO-SOURCE`.
- Diagnostic API-37 runtime: database 20/20, datastore 8/8 and data 36/36 passed.
- Feature and app Compose runs reached 9 tests each, but every test failed during Espresso setup
  with `NoSuchMethodException: android.hardware.input.InputManager.getInstance`; no product
  assertion ran.
- Debug APK SHA-256:
  `74e78ea2c571c9eba1c986d1fdee48131ec27a47b1a795587ff43b64a902178f`.
  `icon_and_image_ideas/` is absent from the APK.
- Room remains version 9; OpenAPI has no drift; Docker Compose config is valid.

## Resume exactly here

On Windows:

```bat
sdkmanager.bat "system-images;android-36;google_apis;x86_64"
avdmanager.bat create avd -n Momentum_API_36 -k "system-images;android-36;google_apis;x86_64"
emulator.exe -avd Momentum_API_36
```

Then verify `adb shell getprop ro.build.version.sdk` returns `36` and run from
`fitness-platform/`:

```bash
make android-connected-test
```

The run must execute database, datastore, data, feature and app suites and report real passing test
counts. If green, update this handoff/status with the exact evidence, commit documentation, push all
local commits, and verify the GitHub Actions run belongs to the final documentation commit. Do not
begin Visual Sprint 2 before that acceptance run. If the API-36 run exposes a product defect, fix
only that defect, rerun the complete relevant matrix, and keep the gate local until green.
