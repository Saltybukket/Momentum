# ADR-015: Android sync checkpoints and guest-secret storage

- Status: Accepted
- Date: 2026-07-13

## Context

Private-exercise pages were applied in Room while their cursor was written to Preferences DataStore. A crash between those stores could advance the cursor without committing all changes. Guest bearer and recovery credentials were also plaintext preferences, and concurrent bootstrap calls could create different recovery values.

## Decision

Room schema 5 adds singleton `sync_state`. The worker reads only its Room cursor and applies every page plus `nextCursor` in one Room transaction. Migration 4→5 initializes cursor 0: the legacy cursor is not trustworthy, so an idempotent full replay is safer than skipping a possible change. The legacy key is removed only after a successful Room checkpoint.

`GuestSecretStore` separates secret persistence from consent and installation preferences. Production uses AES/GCM with a random IV per value and a non-exportable Android Keystore key; JVM logic uses an explicitly named fake. Legacy plaintext values are encrypted, read back and only then deleted. Invalidated ciphertext/key material is discarded, installation identity rotates, local Room content remains intact and a later consented run creates a new session. Credential creation is mutex-protected so concurrent callers observe one local pair.

Private sync remains disabled by default. Opt-in persists consent before enqueuing unique work; opt-out persists it before cancellation. The worker rechecks consent before authentication, push and each pull phase. Public catalog seed/refresh remains independent of private consent and guest authentication.

Persisted worker errors are bounded classifications rather than exception messages, preventing accidental credential disclosure.

## Consequences

A committed cursor always describes committed local changes, upgrades cannot skip pages, and secrets no longer remain in plaintext preferences. A full replay can cost additional network/time once after upgrade. Android Keystore loss requires a new guest session but does not delete offline data. Cancellation of an already-running HTTP request is best-effort; boundary rechecks prevent subsequent private requests after opt-out is observed.
