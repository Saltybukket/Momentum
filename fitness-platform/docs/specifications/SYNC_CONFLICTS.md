# Sync and conflict specification

## Current protocol

- Client creates UUIDs and writes local entity plus outbox atomically.
- Worker sends batches of up to 100 operations.
- Backend upserts by UUID and scopes ownership to the guest/user principal.
- HTTP idempotency key protects the batch; operation UUID protects each mutation.
- Results mark local outbox rows synced/failed.

## Planned pull protocol

The server exposes an opaque per-user change cursor. Changes include aggregate type, UUID, server version, server timestamp and tombstone state. Client applies them transactionally, advances the cursor only after success and never interprets cursor internals.

## Conflict classes

1. **No conflict:** only local or only remote changed since base version.
2. **Field-merge safe:** disjoint profile/settings fields changed.
3. **Collection merge:** children have stable UUIDs and no contradictory edit.
4. **Delete/update:** tombstone wins unless explicit restore creates a new version.
5. **Semantic conflict:** same exercise/workout field changed; preserve both values and request user choice.
6. **Server-authoritative:** rewards/purchases/moderation/integrity always use server result; no merge.

Retries use exponential backoff with jitter and a maximum attempt policy. Permanent validation errors become visible `FAILED`; version mismatch becomes `CONFLICT`; authentication errors pause sync until session recovery.
