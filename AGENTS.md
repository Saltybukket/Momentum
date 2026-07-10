# AGENTS.md

## Project overview

This is a monorepository containing:

- Kotlin Android app in `android/`
- FastAPI backend in `backend/`
- shared seed data in `data/`
- infrastructure in `infrastructure/`

## Required workflow

Before changing code:

1. Read `ARCHITECTURE.md`.
2. Read the relevant module documentation.
3. Inspect existing tests.
4. Do not change documented architectural decisions silently.

After changing code:

1. Run affected unit tests.
2. Run linting and static analysis.
3. Run the relevant build.
4. Update documentation when behavior changes.
5. Report commands and actual results.

## Commands

- Full test suite: `make test`
- Backend tests: `make backend-test`
- Backend lint: `make backend-lint`
- Android unit tests: `make android-test`
- Android build: `make android-build`
- Docker development environment: `make dev`

## Restrictions

- Never commit secrets.
- Do not replace real implementations with static UI mocks.
- Clearly label test and mock providers.
- Do not move required features into FUTURE_FEATURES.md.