# Android client

Offline-first Android scaffold using Kotlin, Compose, Room, DataStore, WorkManager, Retrofit, Hilt and MVVM with unidirectional state.

## Module dependency direction

`app -> feature:main -> domain -> core:model`

`app -> data -> domain/core:*`

`app -> core:sync -> core:database/core:network/core:datastore`

Room is the local source of truth. Network synchronization is an explicit background concern through the outbox.

## Gradle bootstrap

The isolated generation environment could not download the standard Gradle wrapper. The repository therefore includes a small auditable bootstrap at `gradle/wrapper/bootstrap-src/` plus its compiled wrapper JAR. It downloads only the pinned Gradle 8.13 distribution, verifies `distributionSha256Sum`, prevents zip-slip during extraction and then delegates to Gradle. Once Gradle is available, replacing it with the standard generated wrapper is recommended:

```bash
gradle wrapper --gradle-version 8.13 --distribution-type bin
```
