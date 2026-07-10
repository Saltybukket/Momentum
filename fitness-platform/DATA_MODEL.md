# Data model

## Principles

- UUID primary keys support offline creation and idempotent upsert.
- Timestamps are UTC-aware on the server and epoch milliseconds locally.
- User ownership is explicit on private aggregates.
- Pydantic API schemas, domain values and SQLAlchemy rows are separate types.
- Soft deletion is used for custom exercises because offline devices need a future tombstone; other current entities use controlled hard/cascade rules.
- Reward, purchase and audit ledgers will be append-only when introduced.

## Current entities

### `users`

Internal identity root. `kind` distinguishes `GUEST` from future registered accounts. A future identity table can attach Google/email providers without changing content ownership.

### `guest_sessions`

Development guest credentials. Stores token hash, expiry, revocation time and user FK. Raw tokens are never stored.

### `profiles`

Display name, unit system and optional-onboarding state. Sensitive body/health attributes intentionally do not live in this broad profile table.

### `exercises`

Private user-created exercises for this slice. Includes tracking type, muscle/equipment text and soft-delete timestamp. A future curated catalog is a separate content-owned aggregate.

### `workouts` and `workout_exercises`

Minimal workout lifecycle and ordered links to exercises. Future sets/results become child aggregates with their own UUIDs rather than mutable JSON blobs.

### `outbox_events`

Durable backend event records with unique event IDs, type, aggregate ID, JSON payload, creation and publication timestamps. It is the exchange seam for a future broker.

### `idempotency_records`

Stores scoped key hash, request fingerprint, status and response payload. Unique constraints prevent duplicate processing.

## ER diagram

```mermaid
erDiagram
  USERS ||--o{ GUEST_SESSIONS : authenticates
  USERS ||--|| PROFILES : owns
  USERS ||--o{ EXERCISES : creates
  USERS ||--o{ WORKOUTS : performs
  WORKOUTS ||--o{ WORKOUT_EXERCISES : contains
  EXERCISES ||--o{ WORKOUT_EXERCISES : referenced_by
  USERS ||--o{ IDEMPOTENCY_RECORDS : scopes
  USERS ||--o{ OUTBOX_EVENTS : emits

  USERS {
    uuid id PK
    string kind
    datetime created_at
    datetime updated_at
  }
  GUEST_SESSIONS {
    uuid id PK
    uuid user_id FK
    string token_hash UK
    datetime expires_at
    datetime revoked_at
  }
  PROFILES {
    uuid user_id PK_FK
    string display_name
    string unit_system
    string onboarding_status
    string sync_status
    datetime created_at
    datetime updated_at
  }
  EXERCISES {
    uuid id PK
    uuid owner_user_id FK
    string name
    string primary_muscle_group
    string equipment
    string tracking_type
    string sync_status
    datetime deleted_at
    datetime server_updated_at
  }
  WORKOUTS {
    uuid id PK
    uuid owner_user_id FK
    string title
    string status
    datetime start_time
    datetime end_time
    string sync_status
    datetime server_updated_at
  }
  WORKOUT_EXERCISES {
    uuid id PK
    uuid workout_id FK
    uuid exercise_id FK
    int position
  }
  OUTBOX_EVENTS {
    uuid id PK
    uuid event_id UK
    uuid owner_user_id FK
    string event_type
    uuid aggregate_id
    json payload
    datetime created_at
    datetime published_at
  }
  IDEMPOTENCY_RECORDS {
    uuid id PK
    uuid owner_user_id FK
    string key_hash
    string request_fingerprint
    int response_status
    json response_body
    datetime expires_at
  }
```

## Important constraints and indexes

- Unique guest token hash.
- One profile per user.
- Exercise/workout owner indexes for scoped lists.
- Unique `(workout_id, position)` ordering.
- Unique backend event ID.
- Unique idempotency scope over principal/key/method/path.
- Check constraints for nonblank names/titles and valid enum values where represented.
- Foreign keys prevent cross-aggregate orphaning.

The exact SQL is canonical in `backend/alembic/versions/08adec2dab35_initial_scaffold_schema.py`.

## Android local schema

Room contains:

- `guest_profile`
- `custom_exercises`
- `workouts`
- `workout_exercises`
- `sync_outbox`

Every syncable aggregate carries local UUID, optional server ID, sync status and optional conflict version. The outbox carries operation UUID, aggregate UUID, operation type, payload, status, retry count, last error and creation timestamp.

## Planned model boundaries

The following are intentionally not added as generic placeholder tables: nutrition, body measurements, health records, XP ledger, quests, streaks, bosses, social content, moderation, groups, tournaments, products, entitlements, purchases, ad receipts, AI consent and integrity evidence. Each requires domain-specific constraints and retention/privacy rules; creating empty generic tables now would lock in poor semantics.
