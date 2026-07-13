# Backend module boundaries

The executable source of truth is `backend/src/fitness_platform/modules/catalog.py`; CI tests it for cycles.

| Module | Owns | Allowed dependencies | Scaffold status |
|---|---|---|---|
| identity | guest/registered identity and sessions | none | basis implemented |
| user_profile | preferences and optional profile | identity | basis implemented |
| onboarding | optional setup progress | user_profile | contract only |
| exercises | immutable public catalog and owner-scoped private exercises | identity | catalog and private sync slices implemented |
| equipment | equipment taxonomy | none | data contract only |
| training_locations | location equipment inventory | equipment | contract only |
| workout_planning | plans/schedules | exercises, training_locations | contract only |
| workout_execution | workouts/sets/results | exercises | minimal slice implemented |
| activity_tracking | normalized activities | integrations | provider port only |
| step_tracking | step source/aggregation | health_data | contract only |
| nutrition | nutrition records | integrations | provider port only |
| body_measurements | weight/composition | integrations | provider port only |
| health_data | normalized health/provenance | integrations | provider port only |
| integrations | provider adapters | none | ports/mocks implemented |
| analytics | derived projections | workout_execution, health_data | contract only |
| security | integrity/fraud/audit | none | foundation implemented |
| gamification | XP/level/reward ledger | security | contract only |
| quests | definitions/progress | gamification | contract only |
| streaks | streak/freeze lifecycle | gamification, commerce | contract only |
| boss_events | capped global contribution | gamification, security | contract only |
| groups | groups/roles/membership | identity | contract only |
| tournaments | normalized competition | groups, security | contract only |
| moderation | reports/appeals/audit | administration | contract only |
| social | structured posts/reactions | identity, moderation | contract only |
| notifications | notification intent/policy | integrations | provider port only |
| commerce | products/purchases/entitlements | security | provider port only |
| advertising | voluntary ad proof/reward | commerce, security | provider port only |
| administration | audited privileged operations | identity | contract only |
| ai_helper | optional minimized assistant | integrations | provider port only |

Cross-module communication should use application services or domain events, not direct access to another module's tables. Shared low-level utilities must remain genuinely domain-neutral.
