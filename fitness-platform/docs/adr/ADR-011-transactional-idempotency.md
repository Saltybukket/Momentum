# ADR-011: Transactional database idempotency

- Status: Accepted
- Date: 2026-07-12

## Context

Persisting an idempotency reservation, committing a domain mutation, and completing the replay response in separate transactions leaves a crash window in which the mutation exists but its response cannot be replayed. Raw client keys also needlessly expose caller-selected identifiers at rest, and expired records otherwise grow without bound.

## Decision

For the current database-only idempotent endpoints, reservation, domain mutation, transactional outbox/domain-event row, serialized response, and `COMPLETED` state share one SQLAlchemy unit of work. A failure before completion rolls the entire transaction back. In-process domain dispatch happens only after commit; future external side effects must consume a transactional outbox and must not run inside this transaction.

The persisted key is `SHA-256(scope || NUL || normalized-key)`. Scope binds HTTP method, canonical route, and authenticated principal. Request fingerprints bind the payload. Raw header values are validated but are neither stored nor logged. Revision `a91c4e2f6b70` originally hashed legacy values in place, but a raw 64-character lowercase hexadecimal key is indistinguishable from its output. Revision `c83f7a6d219b` therefore purges all pre-existing idempotency rows. They are operational replay data with a 24-hour lifetime, so a controlled retry is safer than retaining an ambiguous credential-like identifier.

`IN_PROGRESS` owns a five-minute lease. A concurrent caller receives a controlled conflict while that lease is live. `COMPLETED` replays the stored status/body. A stale `IN_PROGRESS` transitions to `FAILED`; `FAILED` requires a new key. `EXPIRED` is a logical terminal condition represented by deletion after the 24-hour retention deadline, either on reuse or through the bounded maintenance command `make idempotency-cleanup` (default batch 500).

## Consequences

Pure database mutations no longer have a commit/completion crash gap and emit at most one persisted domain/outbox event. PostgreSQL and SQLite use conflict-safe inserts for reservations. One-way key hashing means a downgrade cannot reconstruct original raw keys; the rows remain opaque and ephemeral, while schema compatibility is unchanged.
