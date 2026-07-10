# Security foundation and threat model

## Scope

This document describes the scaffold security posture. It is not a claim of production readiness. The temporary guest-token flow, local HTTP emulator exception and absent Play Integrity implementation are explicit limitations.

## Trust boundaries

```mermaid
flowchart LR
  Device[Potentially modified Android device] -->|untrusted requests| Edge[API boundary]
  Edge --> App[Validated application services]
  App --> DB[(Canonical PostgreSQL)]
  App --> Cache[(Non-authoritative Redis)]
  App -. future .-> Vendor[External provider]
  Admin[Privileged operator] -. future audited path .-> App
```

The Android client, local Room database, imported provider payloads and all request headers are untrusted. PostgreSQL is canonical for server-authoritative values.

## Current controls

- Environment-based configuration and `.env.example`; no committed secrets.
- Production startup rejects the development guest-token pepper.
- Random guest bearer tokens; only hashes are persisted.
- Strict Pydantic request schemas and application validation.
- SQLAlchemy parameterization and explicit ownership queries.
- Request IDs and structured logs without raw tokens.
- Controlled CORS allowlist and methods/headers.
- Redis-backed rate-limit seam with safe application fallback.
- Idempotency key request fingerprint and unique database constraints.
- UUID ownership checks and soft-delete rules.
- Safe error envelope without stack traces/SQL details.
- Dependency/secret scans in CI.
- Android release architecture expects R8/ProGuard and no embedded provider secrets.

## Threat table

| Threat | Current mitigation | Remaining work |
|---|---|---|
| Manipulated API request | schemas, ownership checks, server-side state transitions | integrity evidence and risk scoring for sensitive future actions |
| Replay/double submit | idempotency keys, operation IDs, unique event IDs | nonce/request-content binding for purchases/rewards |
| Duplicate sync | UUID upsert, idempotency record | pull cursor, tombstones and conflict-version enforcement |
| Manipulated local DB/APK | local values treated as claims, not future rewards | Play Integrity, anomaly detection, signed sensitive responses |
| Stolen guest token | hashed storage, expiry/revocation fields | secure device storage, rotation, session UI, account recovery/linking |
| Mass access/DoS | rate-limit foundation, pagination | edge rate limiting, quotas, WAF, circuit breakers and load tests |
| Injection | typed schemas, ORM parameterization | SAST/DAST and provider payload fuzzing |
| Secret leakage | `.gitignore`, env abstraction, gitleaks | managed secret store, rotation runbooks, environment isolation |
| Sensitive logs | structured event fields and no raw bearer logging | automated log redaction tests and retention controls |
| Malicious provider payload | provider normalization boundary | signature validation, schema/version limits, quarantine queue |
| Purchase/ad fraud | provider ports only | server receipt/callback verification and unique ledgers |
| XP/step farming | no reward implementation yet | server-authoritative rule engine, caps, provenance and anomaly review |

## Server-authoritative future values

The following must never be accepted as final client values: XP, level, currency balance, quest completion, boss damage, tournament/leaderboard score, entitlements, purchase state, ad rewards and paid streak freezes. Clients submit source events/evidence; the backend validates and writes append-only ledgers.

## Anti-cheat extension points

`IntegrityProvider` will normalize Play Integrity evidence and future risk signals. Sensitive requests should include request content binding, freshness, request UUID and verified principal. Root/emulator/hooking indicators are risk signals only, not an automatic permanent ban.

Planned response ladder:

1. mark event/risk evidence;
2. withhold only the disputed reward;
3. request additional validation;
4. inform the user;
5. allow appeal;
6. restrict only with strong, reviewable evidence.

Feature flags and a kill switch must disable abused reward paths without disabling basic local fitness tracking.

## Android token and transport plan

- Guest token currently resides in DataStore to keep the scaffold simple.
- Before production, refresh/access credentials move to Android Keystore-backed encrypted storage and are rotated.
- Production traffic is HTTPS only.
- The manifest permits cleartext solely for emulator `10.0.2.2`/localhost local development.
- Certificate pinning, if adopted, requires backup pins and remote rotation; a brittle single pin is worse than platform trust.
- Credential Manager performs Google credential acquisition; backend validates the Google token and issues platform sessions.

## Password architecture (prepared, not implemented)

Future email login uses a modern password hash such as Argon2id, per-user salt, optional server-side pepper, email verification, reset-token hashing, rate limits and account-enumeration-resistant responses. Passwords never transit to logs or provider adapters.

## Security review checklist before staging

- Replace guest-token development flow or isolate it behind a non-production flag.
- Configure managed secrets and rotate all local defaults.
- Enforce HTTPS and strict staging CORS.
- Add production PostgreSQL/Redis TLS and least-privilege credentials.
- Add session rotation/revocation and secure Android storage.
- Add SAST, DAST, container/image and SBOM review.
- Threat-model each new health, social, purchase, reward and AI slice.
- Add audit retention and privileged-action approval rules.
- Run abuse, rate-limit, fuzz and restore tests.

## Reporting

Do not open a public issue containing a live secret or exploitable personal-data path. A private security contact/process must be defined before public release.
