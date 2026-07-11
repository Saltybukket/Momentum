# Android client

Offline-first Android scaffold using Kotlin, Compose, Room, DataStore, WorkManager, Retrofit, Hilt and MVVM with unidirectional state.

## Module dependency direction

`app -> feature:main -> domain -> core:model`

`app -> data -> domain/core:*`

`app -> core:sync -> core:database/core:network/core:datastore`

Room is the local source of truth. Network synchronization is an explicit background concern through the outbox.

## Build

The repository uses the standard Gradle 8.13 wrapper with a pinned distribution checksum. Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to an Android SDK containing platform 36, then run:

```bash
./gradlew test lintDebug assembleDebug
```

## Connected tests from WSL

Use a Windows-hosted emulator with WSL mirrored networking. The repository script defaults to
the Windows ADB server (`tcp:127.0.0.1:5037`), validates a device in the `device` state, builds
only real instrumentation modules and runs their registered runners directly through ADB. It
avoids UTP installation when an external emulator's console authentication is unavailable.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ADB_SERVER_SOCKET="tcp:127.0.0.1:5037"
../scripts/android-connected-tests.sh core:database
```

The current real instrumentation modules are `core:database`, `data` and `app`. Their raw output is
stored under `android/build/connected-test-results/`. `connectedProjectAndroidTest` is the Gradle
aggregation task for those three modules; empty Android-test source sets are skipped. The project
baseline is a stable API-36 Google APIs/x86_64 AVD; pass `ANDROID_SERIAL` when more than one
device is attached.
