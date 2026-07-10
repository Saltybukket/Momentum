# Future server-authoritative extension contracts

This specification plans boundaries only; none of these features is implemented.

## Reward ledger

A future immutable `reward_transactions` ledger uses a unique tuple such as `(user_id, source_event_id, reward_type, rule_version)`. Balance/XP/level are projections. Reprocessing the same event returns the existing transaction. Caps and diminishing returns are versioned configuration with effective dates and admin audit.

## Quests and streaks

Quest definitions are content/configuration; assignments snapshot the rule version and safe target. Progress consumes qualified events. Streak evaluation uses user time zone, planned rest days and explicit grace/freeze intervals. Long-term freeze purchase creates an entitlement/activation record; client price and client “paid” flags are never trusted.

## Bosses and tournaments

Boss damage and tournament points are server projections over qualified, deduplicated activity facts. Per-user/day/source caps and participation factors are applied before aggregation. Public output exposes normalized contributions, not raw health records. Recalculation is deterministic from the ledger and rule version.

## Commerce and advertising

Product catalog and store identifiers map to backend products. Purchase receipt/provider callbacks create verified purchase records, then entitlements. Refunds/revocations are events, not row deletion. Virtual currency uses a double-entry or append-only transaction ledger. Rewarded ads require a unique verified completion proof and daily cap.

## AI helper

AI requests pass through feature flag, consent, data selection/minimizer, safety classifier, provider budget and response policy. A local deterministic layer answers app navigation/common questions first. `AiProvider` receives no stable account ID and no health data unless explicitly selected for that request. “AI disabled” is tested as zero provider calls.

## Integrity and fraud

Sensitive event requests attach principal, request UUID, timestamp/freshness, content hash and optional Play Integrity evidence. Risk is evidence, not guilt. The system can hold a reward, request verification and support appeal without blocking local workout access.
