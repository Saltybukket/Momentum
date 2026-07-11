# Sync and conflict specification

## Current protocol

- Client creates UUIDs and writes local entity plus outbox atomically.
- Worker sends batches of up to 100 operations.
- Backend upserts by UUID and scopes ownership to the guest/user principal.
- HTTP idempotency key protects the batch; operation UUID protects each mutation.
- Results mark local outbox rows synced/failed.

## Exercise pull protocol

Private exercises use a per-user, monotonic change-feed cursor. A page contains the exercise UUID,
revision, server timestamps and tombstone state. The Android worker pushes pending operations before
pulling, applies a complete page and cursor in one Room transaction, and leaves the cursor unchanged
if that transaction fails. Repeating a page is idempotent. The cursor is an implementation value and
must not be inferred by clients.

Profile and workout pull synchronization are not part of this slice.

## Persisted exercise conflicts

An optimistic-revision mismatch returns the current server exercise in the sync result. The client
then persists one `exercise_conflicts` row per exercise with:

- `conflict_id`, `exercise_id`, `conflict_type`, local and remote revisions;
- complete local and remote snapshots;
- detection time, resolution status and optional resolution time.

The conflicting outbox operation changes to `CONFLICT`; it is not retried automatically. The
exercise remains visibly conflicted after a process restart. Conflict types are `BOTH_MODIFIED`,
`REMOTE_DELETED_LOCAL_MODIFIED`, `LOCAL_DELETED_REMOTE_MODIFIED` and `REVISION_MISMATCH`.

### Resolution rules

- **Keep local:** remove only unacknowledged operations for that exercise, enqueue the retained
  local snapshot with the current remote revision as its base, and retain the conflict until the
  server acknowledges that new operation.
- **Take server:** atomically replace the local row with the stored server snapshot, remove only
  unacknowledged operations for that exercise and mark the conflict resolved.
- **Manual merge:** save an explicit user-edited snapshot against the current remote revision and
  retain the conflict until acknowledgement.

No resolution silently discards a snapshot. UI access is through an exercise-list badge and a
comparison screen with accessible labels and confirmation.

## Conflict classes

1. **No conflict:** only local or only remote changed since base version.
2. **Field-merge safe:** disjoint profile/settings fields changed.
3. **Collection merge:** children have stable UUIDs and no contradictory edit.
4. **Delete/update:** tombstone wins unless explicit restore creates a new version.
5. **Semantic conflict:** same exercise/workout field changed; preserve both values and request user choice.
6. **Server-authoritative:** rewards/purchases/moderation/integrity always use server result; no merge.

Retries use exponential backoff with jitter and a maximum attempt policy. Permanent validation errors become visible `FAILED`; version mismatch becomes `CONFLICT`; authentication errors pause sync until session recovery.
