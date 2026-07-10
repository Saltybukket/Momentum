# ADR-010: Temporary guest authentication foundation

- Status: Accepted with explicit production prohibition
- Date: 2026-07-10

## Context

The slice needs authenticated ownership/sync before Google/email login is implemented. Treating anonymous requests as globally shared would invalidate ownership and migration design.

## Decision

Issue a random, expiring development guest bearer token, store only its hash and map it to an internal guest user/profile. Mark responses and documentation as development-only. Prepare Credential Manager dependencies and a future identity-link flow.

## Consequences

The API exercises real principal scoping and guest migration has a stable ownership root. The mechanism lacks production identity assurance/recovery and must be replaced or isolated before any shared deployment. Android token storage must move to Keystore-backed protection.
