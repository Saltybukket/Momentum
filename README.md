# Momentum

Momentum is an offline-first fitness platform under active development. The implementation lives in
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
- [Current status](fitness-platform/docs/STATUS.md) — implemented scope, schema heads and risks
- [Research reference](Research/FITNESS_RESEARCH_REFERENCE.md) — product research source material

Private custom exercises support owner-scoped push/pull, tombstones and durable conflict choices;
workouts and profiles retain narrower synchronization semantics. The anonymous public exercise
catalog uses immutable backend releases and a hash-verified atomic Room cache.
