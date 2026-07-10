# ADR-004: Room is the Android local source of truth

- Status: Accepted
- Date: 2026-07-10

## Context

The workout experience must work offline and survive process/device restarts. A network-first cache cannot satisfy this reliably.

## Decision

UI observes Room through repositories. User writes commit to Room immediately. Network responses update Room rather than bypassing it. DataStore is reserved for small session/preferences values, not relational fitness data.

## Consequences

Offline behavior is consistent and testable. Schema migrations and conflict metadata become first-class responsibilities. Server-authoritative rewards remain provisional until synced/verified.
