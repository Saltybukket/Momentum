# Integration architecture

## Rule: official adapters only

The domain never calls a vendor SDK/API directly. Every integration implements a narrow provider port and emits normalized records with source/provenance. No YAZIO or RENPHO password is stored, and no undocumented/reverse-engineered endpoint is an acceptable production dependency.

## Provider ports

| Port | Responsibility | Current implementation |
|---|---|---|
| `HealthDataProvider` | normalized health records and permissions | deterministic mock |
| `NutritionProvider` | calories, macros, meals, hydration | deterministic mock |
| `ActivityProvider` | activities, steps and active time | deterministic mock |
| `BodyMeasurementProvider` | weight/composition with measurement provenance | deterministic mock |
| `AuthenticationProvider` | guest and future Google/email identity proof | deterministic mock / Android Credential Manager dependency prepared |
| `PaymentProvider` | products, receipt verification and restore | contract only |
| `AdvertisementProvider` | verified voluntary rewarded-ad completion | contract only |
| `NotificationProvider` | local/push delivery | contract only |
| `IntegrityProvider` | request-bound integrity evidence/risk signals | contract only |
| `AiProvider` | optional minimized assistant requests | contract only |

Mocks live in `backend/src/fitness_platform/providers/` and are deterministic for tests. They are clearly named `Mock*` and must never be selected in production accidentally.

## Health Connect

Planned primary Android health hub. The adapter belongs on device for permissions/read operations, while normalized imports and provenance can sync to the backend with explicit consent.

Architecture requirements:

- request only permissions for enabled features;
- record source app/device and original record ID;
- incremental change tokens/cursors;
- import deletion/change propagation;
- duplicate key over provider + source record ID + version;
- no permanent background service; use platform-supported scheduling;
- a test mode using deterministic records.

Not implemented in this scaffold.

## Garmin

Planned `GarminProvider`/webhook adapter behind OAuth/token storage and signature verification. A mock server and realistic licensed/synthetic payloads should be introduced before production credentials. Token refresh, webhook replay protection and activation process belong in a dedicated slice.

No real Garmin access or production connector is included.

## YAZIO

Preferred path is nutrition records exposed through Health Connect. The architecture reserves:

- `HealthConnectNutritionProvider`;
- `ManualNutritionProvider`;
- `MockYazioNutritionProvider`;
- a future `OfficialYazioProvider` only if an authorized official interface exists.

No unofficial API, scraping or password storage is present.

## RENPHO and smart scales

Preferred path is body measurements that the companion app publishes through Health Connect. Planned adapters:

- Health Connect body measurement provider;
- manual entry;
- CSV import;
- deterministic RENPHO mock;
- future official RENPHO provider if authorized.

Every value must identify whether it was measured, device-calculated, app-estimated or manually entered. No unknown RENPHO endpoint is used.

## Other providers

Fitbit, Samsung Health, Strava, Polar, Suunto, COROS, Withings, Oura and future Apple Health are adapter candidates. They are not represented by meaningless source files; their shared capability contracts are captured by the provider ports and module catalog. Add a concrete module only when an official integration slice starts.

## Payments and Play Billing

`PaymentProvider` will separate Google Play Billing client operations from backend purchase verification. Required future concepts are products, one-time purchases, subscriptions, lifetime/promotional/tester entitlements, restore, refunds and an audit trail. Store price/currency is authoritative; the Android client never decides entitlement.

Not implemented.

## Rewarded ads

`AdvertisementProvider` will expose a verified completion proof. The server grants a capped reward using a unique ad-completion ID. Consent, daily limits and exclusion from workout/health screens are policy inputs. No ad SDK is included.

## Notifications

A future notification port separates domain notification intent from local scheduling/FCM delivery. User categories, quiet hours, limits, opt-out and non-shaming copy are domain policy. No push service is included.

## AI

`AiProvider` is optional and replaceable. Planned pipeline:

```text
local deterministic rule -> consent/feature flag -> data minimizer -> provider budget/rate limiter -> safe response policy
```

The app must remain fully functional with AI disabled. No model/API call is implemented.

## Adding an adapter

1. Define/extend normalized capability types without vendor fields.
2. Add a deterministic fake and contract tests.
3. Implement the official adapter in infrastructure.
4. Store credentials only through the secret/token abstraction.
5. Add consent, provenance, deletion and rate-limit behavior.
6. Document provider terms, activation, data flow and operational alerts.
7. Register through dependency injection/feature flags; domain code remains unchanged.
