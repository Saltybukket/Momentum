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
