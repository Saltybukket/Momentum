# Momentum

Momentum is an offline-first fitness platform scaffold. The implementation lives in
[`fitness-platform/`](fitness-platform/): a FastAPI/PostgreSQL/Redis backend and a modular
Kotlin/Compose Android client.

## Start here

```bash
cd fitness-platform
cp .env.example .env
docker compose up --build
```

The backend health endpoint is available at <http://localhost:8000/health>. Development guest
sessions are intentionally local-only; do not use them as production authentication.

For Android, set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to an SDK with API 36 installed, then:

```bash
cd fitness-platform/android
./gradlew test lintDebug assembleDebug
```

## Documentation

- [Platform README](fitness-platform/README.md) — setup and verification commands
- [Architecture](fitness-platform/ARCHITECTURE.md) — boundaries and module direction
- [Implementation report](fitness-platform/docs/IMPLEMENTATION_REPORT.md) — delivered scaffold and constraints
- [Research reference](Research/FITNESS_RESEARCH_REFERENCE.md) — product research source material

The first implemented synchronization slice covers private custom exercises: outbox push,
optimistic server revisions, cursor-based pull, tombstones and local conflict state. Workouts and
profiles retain their existing push-only scaffold semantics.
