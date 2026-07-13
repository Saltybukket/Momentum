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

The release hash covers one semantic projection, independent of submitted array order. JSON object
keys are lexicographic; muscles and equipment are ordered by slug; exercises by
`(source, external_id, id)`; exercise muscles by `(slug, role)`; and exercise equipment
lexicographically. The compact JSON is UTF-8 and contains only `schema_version`,
`catalog_version`, normalized UTC `published_at`, `batch_id`, facets and exercises. Response-only
fields such as `total`, `complete`, `sources` and `licenses` are not part of the projection. The
importer, public snapshot and Android verifier implement this same contract. A shuffled submission
therefore has the same hash while any semantic content change has a different hash.

Catalog text uses the shared single-line/multiline control and bidirectional-character policy.
License links must be absolute HTTPS URLs with a valid hostname and port and without userinfo.
Validation and normalization happen before hashing or staging.

Android accepts only a complete snapshot whose canonical SHA-256 matches `content_hash`. It
validates identifiers, text, publication/review state, licenses and relations before the Room
transaction. Invalid or interrupted refreshes preserve the previous cache. Private custom
exercises remain in separate owner-scoped tables and APIs.

The reversible migration projects the active release into the single-snapshot legacy catalog
before dropping release-scoped tables. Facet UUIDs are deterministically derived from their slugs.
On re-upgrade, the release marked `PUBLISHED` by the downgrade is restored as the active release;
the migration never substitutes a merely newer retired release. An empty or absent activation is
preserved as empty.

Because revision `0d4f6a8b2c17` has not shipped in a public release and the defect is in that
revision's own downgrade/round-trip contract, its migration implementation is corrected in place.
The schema and revision identity are unchanged; existing development databases require no forward
schema operation, while future downgrade executions gain the data-preserving behavior.

## Consequences

- Full releases are authoritative: content omitted from the newly active release disappears from
  public queries without deleting history.
- Snapshot content, manifest identity and ETag cannot mix across versions.
- Release storage grows append-only and requires a future explicit retention policy.
- Operational rollback is `python -m fitness_platform.catalog_import --activate-version VERSION`.
- The legacy global catalog tables remain only for reversible migration compatibility; no runtime
  repository writes them.
- A downgrade can represent only the active snapshot in the legacy schema. Retired release
  metadata remains, but its release-scoped content is unavailable until restored from a source
  artifact; the active release itself survives downgrade and re-upgrade with count and hash
  consistency.
