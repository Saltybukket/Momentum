# Implementation plan

Each phase is delivered as small vertical slices that leave the system runnable. Features listed in the product master specification remain mandatory future product scope; they are not reclassified as optional merely because this architecture assignment stops at the scaffold.

## Phase 1 — Basis architecture (current)

Slices:

1. Monorepository, environment configuration, Docker and CI.
2. Backend identity/profile/exercise/workout foundation.
3. Android local guest/exercise/workout foundation.
4. Push outbox, domain events and deterministic provider mocks.
5. Architecture/security/privacy/testing documentation.

Definition of done: backend checks pass, migration works, Android project/build/tests run in an SDK-equipped environment, Docker stack is smoke-tested, and limitations are recorded without claiming future features complete.

## Phase 2 — Exercise library and workout planning

Slices:

- Licensed exercise/muscle/equipment import pipeline with provenance and schema validation.
- Curated catalog versus private custom-exercise ownership.
- Training locations/equipment inventory and compatible alternative query.
- Plan aggregate: days, blocks, ordered exercises, sets/reps/RPE/RIR/rest/tempo.
- Initial editable demo plans and time/equipment adaptation rules.

DoD: offline browse/filter, license report, migration, catalog/admin import API, accessibility, unit/integration/UI tests and no unlicensed media.

## Phase 3 — Full workout execution

Slices:

- Workout sets and tracking types.
- Timers, pause/resume, warm-up/work/drop/superset/circuit structure.
- Resume after process death and crash-safe transactional writes.
- Personal-record detection and summary.
- Optional structured post draft event, but no public social feed yet.

DoD: complete offline flow, deterministic lifecycle, no duplicate completion event, recovery tests and performance baseline.

## Phase 4 — Synchronization and accounts

Slices:

- Pull cursor/change feed and tombstones. **Delivered for private custom exercises.**
- Conflict versioning/resolution UI. **Delivered for private custom exercises; profile and workout conflicts remain future slices.**
- Secure session/token storage and rotation.
- Credential Manager Google login plus backend token validation.
- Email registration/verification/reset.
- Atomic guest-to-account migration and duplicate-account merge policy.

DoD: multi-device tests, offline conflict matrix, account export/delete basics, abuse/rate-limit tests and removal of development guest auth from production configuration.

## Phase 5 — Health Connect and body data

Slices:

- Permission/capability UX and Health Connect test provider.
- Steps/activities import with provenance/dedup/change tokens.
- Weight/body measurement domain with measured/calculated/estimated/manual classification.
- Manual and CSV body measurement adapters; RENPHO via Health Connect path.
- Garmin mock server/webhook contract and activation documentation.

DoD: granular consent, deletion propagation, background/battery tests, adapter contracts and no unofficial vendor APIs.

## Phase 6 — Nutrition and analytics

Slices:

- Manual daily values, meals, recurring meals, custom food/recipes/portions.
- Health Connect nutrition import and YAZIO mock/official seam.
- Formula library with source, units, limitations and tests.
- Training/activity/body/nutrition/regeneration projections and period queries.
- Local basic analytics plus server projection jobs/cache.

DoD: license-safe data, non-medical wording, ranges for uncertain estimates, privacy controls and reproducible calculations.

## Phase 7 — Gamification

Slices:

- Append-only XP/currency/reward ledger and rule-version service.
- Levels and cosmetic entitlement grants.
- Daily/weekly/adaptive quests with safe profile bounds.
- Streaks/rest-day rules and annual free long-term freeze state machine.
- Achievements and return-after-pause rewards.

DoD: server authority, dedupe source-event constraints, caps/diminishing returns, time-zone/year tests, audit trail and no pay-to-win functionality.

## Phase 8 — Social, groups and boss events

Slices:

- Friend/block/privacy model and invitation codes.
- Groups/roles and bounded motivational messages.
- Structured posts/reactions, rate limits and moderation workflow.
- Global boss projection with per-source/day caps and contribution tiers.
- Fair group tournament scoring, matchmaking, seasons and appeals.

DoD: minor/community safety review, moderation/admin queue, privacy-by-default query tests, anti-cheat validation and normal-user-accessible rewards.

## Phase 9 — Commerce, voluntary ads and AI

Slices:

- Product catalog, entitlements and virtual-currency ledger.
- Play Billing purchase/restore/refund verification.
- Tester lifetime grants with feedback/audit workflow.
- Voluntary rewarded-ad verification and daily limits.
- Optional AI consent/minimization/budget/safety pipeline with mock/local fallback.

DoD: receipt/ad proof replay tests, server prices/entitlements, AI-off no-network test, refund/revoke behavior, consent and cost dashboards.

## Phase 10 — Security, privacy and release

Slices:

- Play Integrity request binding and risk pipeline.
- Fraud/anomaly review, staged response and appeal.
- Complete export/delete/provider revocation jobs.
- Production observability, backups/restore, load/chaos tests.
- Accessibility, localization, store disclosure and release signing.
- Security/privacy/legal review and staged rollout/kill switches.

DoD: production threat model, incident/restore exercises, signed release, Play policy checks, privacy documentation and monitored staged rollout.

## Completed slice: offline exercise catalog foundation

Backend catalog/import/API, Alembic migration, Android Room/network/repository/UI, tests and documentation are implemented. The sole environment-only follow-up is connected UI acceptance after installing a stable Windows API-36 system image/AVD; it does not block backend, JVM, lint, migration or build checks.
