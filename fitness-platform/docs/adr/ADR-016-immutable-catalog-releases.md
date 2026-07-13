# ADR-016: Immutable authoritative catalog releases

- Status: Accepted
- Date: 2026-07-13

## Context

The original catalog importer updated global exercise and relation rows in place. A failed or
concurrent import could therefore expose mixed manifest/content state, and removing an exercise
from a full release had no safe historical rollback path.

## Decision

Each import is validated before persistence and written into release-scoped muscle, equipment,
exercise and relation tables. `catalog_version`, `content_hash` and `batch_id` identify an
immutable release. Reimporting an identical version/hash is a no-op; conflicting identities are
rejected.

`catalog_activation` is the single authoritative pointer. Staging and activation run under a
transactional release lock; public pages, facets, details and snapshots resolve one active
version and only read its rows. Newer imports activate automatically, older imports remain
retired, and an explicit activation command permits atomic rollback. At most one release has
`ACTIVE` status, enforced by a PostgreSQL/SQLite partial unique index in addition to the pointer.

Android accepts only a complete snapshot whose canonical SHA-256 matches `content_hash`. It
validates identifiers, publication/review state, licenses and relations before the Room
transaction. Invalid or interrupted refreshes preserve the previous cache. Private custom
exercises remain in separate owner-scoped tables and APIs.

## Consequences

- Full releases are authoritative: content omitted from the newly active release disappears from
  public queries without deleting history.
- Snapshot content, manifest identity and ETag cannot mix across versions.
- Release storage grows append-only and requires a future explicit retention policy.
- Operational rollback is `python -m fitness_platform.catalog_import --activate-version VERSION`.
- The legacy global catalog tables remain only for reversible migration compatibility; no runtime
  repository writes them.
