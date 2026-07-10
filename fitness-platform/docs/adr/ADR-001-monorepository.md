# ADR-001: Use a monorepository

- Status: Accepted
- Date: 2026-07-10

## Context

Android, backend, contracts, demo data, infrastructure and documentation evolve together during the architecture and early product phases. Cross-repository version coordination would add release/process overhead before independent teams exist.

## Decision

Use one repository with explicit top-level boundaries (`android`, `backend`, `data`, `docs`, `infrastructure`, `shared`, `tests`). CI jobs remain separable and paths can later drive selective execution.

## Consequences

Atomic API/client/migration changes and one review context are simpler. Repository tooling must avoid forcing every developer to install every stack. A future split remains possible only after contracts/team ownership stabilize.
