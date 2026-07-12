# ADR-010: Temporary guest authentication foundation

- Status: Accepted with explicit production prohibition
- Date: 2026-07-12

## Context

The slice needs authenticated ownership/sync before Google/email login is implemented. Treating anonymous requests as globally shared would invalidate ownership and migration design.

## Decision

Issue a random, expiring development guest bearer token, store only its hash and map it to an internal guest user/profile. Recovery uses a stable installation identifier plus a separately generated secret whose hash is stored server-side. A successful recovery rotates the bearer token and invalidates the previous token.

Create and recovery are serialized per installation. The application rejects concurrent work in the same process, while PostgreSQL uses a transaction-scoped advisory lock for cross-process serialization. Credential rotation additionally uses compare-and-swap on the previous token hash. Exactly one request in a concurrent burst may create or rotate; competing requests receive `409 CONFLICT`. Creation returns `201`, genuine recovery returns `200`, invalid proof returns `401`, and expected contention must never become `500`. This deliberately favors an explicit retry over returning a credential that another request in the same burst has already invalidated.

Logs contain request metadata but never bearer tokens or recovery secrets. The OpenAPI contract documents all four outcomes. Mark responses and documentation as development-only. Prepare Credential Manager dependencies and a future identity-link flow.

## Consequences

The API exercises real principal scoping and guest migration has a stable ownership root. Clients retry `409` with bounded backoff. SQLite serialization is process-local and intended for development/tests; PostgreSQL is the supported multi-process concurrency boundary. The mechanism still lacks production identity assurance and must be replaced or isolated before any shared deployment. Android token and recovery-secret storage must move to Keystore-backed protection.
