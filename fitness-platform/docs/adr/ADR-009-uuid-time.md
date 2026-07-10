# ADR-009: Client-generated UUIDs and UTC time strategy

- Status: Accepted
- Date: 2026-07-10

## Context

Offline entities need identifiers before reaching the server. Time-sensitive streak/reward logic will span devices/time zones and cannot rely on naive local timestamps.

## Decision

Use UUID strings for aggregate/event/operation IDs. Persist server timestamps as timezone-aware UTC and Android timestamps as epoch milliseconds. Inject clock and UUID providers. User time zone is future explicit profile/configuration input for calendar rules.

## Consequences

Offline upsert and deterministic tests are straightforward. UUID ordering is not assumed. Future local-day calculations must preserve source instant and applicable time zone/rule version.
