# ADR-008: Layered deterministic test strategy

- Status: Accepted
- Date: 2026-07-10

## Context

Offline synchronization and future reward/commerce logic are failure-prone. End-to-end-only testing would be slow and hard to diagnose; mock-only tests would miss persistence/protocol behavior.

## Decision

Use many unit/application tests, targeted repository/API/Room/migration integration tests, provider contract suites and a small E2E smoke layer. Inject clocks/UUIDs and use deterministic fakes. CI runs formatting, lint, typing, migrations, tests, scans and builds.

## Consequences

Business rules are fast to test while critical seams remain covered. Test fixtures and contracts require maintenance. No test/build result may be reported unless actually executed.
