# Contributing

## Workflow

1. Create a focused branch and vertical-slice issue.
2. Update an ADR before introducing a new foundational dependency or reversing an accepted decision.
3. Keep domain/application logic independent from frameworks and vendors.
4. Add tests at the lowest useful layer and a contract/integration test at external seams.
5. Update relevant docs, OpenAPI and migration in the same change.
6. Run `make lint` and `make test`; do not suppress failures without a documented reason.

## Architecture rules

- No FastAPI/SQLAlchemy imports in backend domain code.
- No Compose/Room/Retrofit types in Android domain contracts.
- External providers implement ports; no vendor calls from use cases.
- No direct client writes of server-authoritative reward/commerce values.
- No new empty modules/tables merely to show a future feature name.
- Cross-module imports must follow `ARCHITECTURE.md` and keep the module catalog acyclic.
- Every sync mutation needs stable UUID/idempotency semantics.

## Database changes

- Modify SQLAlchemy metadata and generate/review an Alembic migration.
- Test upgrade from an empty database and from the prior revision when relevant.
- Avoid destructive migration; provide backfill/rollback strategy.
- Add ownership, uniqueness and index reasoning to `DATA_MODEL.md`.

## API changes

- Preserve `/api/v1` compatibility or introduce a new version.
- Reject unknown input fields; document additive response fields.
- Use the common error envelope/request ID.
- Idempotent writes must include tests for duplicate keys and payload mismatch.
- Regenerate `shared/openapi.json` with `make openapi`.

## Android changes

- Room remains source of truth for offline-supported data.
- A successful local write queues outbox work atomically.
- Expose immutable UI state and explicit user actions.
- Add accessibility labels/semantics and avoid color-only meaning.
- Instrumentation tests cover persistence/navigation where JVM tests are insufficient.

## Security, privacy and data

- Never commit credentials or production data.
- New sensitive categories require consent, retention, export and deletion updates.
- Third-party data/media requires source, license report and attribution updates.
- Logging must not include tokens, health payloads, passwords or full AI prompts.
- New privileged actions require audit records.

## Commit and pull-request expectations

Use clear imperative commits. PR description should state scope, architecture impact, tests actually run, migration/API changes, security/privacy impact and known limitations. “All tests pass” is acceptable only with the commands/output available in CI.
