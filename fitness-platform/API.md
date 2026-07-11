# API conventions

## Base URL and versioning

All product routes use `/api/v1/`. The unversioned `GET /health` route is operational. Breaking changes require a new major route prefix; additive schema fields may be introduced within v1 because clients must ignore unknown response fields.

Interactive OpenAPI is available at `/docs` only when `FITNESS_ENABLE_DOCS=true`. The contract can be exported to `shared/openapi.json` with `make openapi`.

## Authentication foundation

`POST /api/v1/guest-sessions` returns a random bearer token. Only a SHA-256-derived token hash is stored. This mechanism is explicitly **development-only**: it has no production identity proof, rotation UI, device binding or recovery. Future Google Credential Manager and email identities map to the same internal user/profile ownership model.

Authenticated request:

```http
Authorization: Bearer <development-guest-token>
```

## Request IDs

Clients may send `X-Request-ID`. The server validates or creates one and returns it in the response. Logs use the request ID, but never bearer tokens or sensitive payloads.

## Idempotency

Relevant writes accept:

```http
Idempotency-Key: <opaque unique key>
```

The key is scoped to principal, method and path. A repeated request returns the recorded response; reusing a key for a different payload is rejected. Keys must contain no personal data. Sync operations also carry stable `operation_id` UUIDs.

## Error format

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The request contains invalid data.",
    "details": [],
    "request_id": "5d3e0f4a-..."
  }
}
```

Expected codes include `VALIDATION_ERROR`, `NOT_FOUND`, `UNAUTHORIZED`, `CONFLICT`, `RATE_LIMITED`, `IDEMPOTENCY_CONFLICT` and `INTERNAL_ERROR`. Internal exception text and SQL details are not returned.

## Pagination

List endpoints use `limit` and `offset`. Responses contain:

```json
{
  "items": [],
  "page": {"limit": 50, "offset": 0, "total": 0}
}
```

Cursor pagination may replace offset for high-volume timelines, but not silently within the same endpoint contract.

## Endpoint overview

| Method | Route | Authentication | Idempotency | Status |
|---|---|---:|---:|---|
| GET | `/health` | no | no | implemented |
| POST | `/api/v1/guest-sessions` | no | yes | development basis |
| GET | `/api/v1/profile` | guest token | no | implemented |
| PUT | `/api/v1/profile` | guest token | yes | implemented |
| GET | `/api/v1/exercises` | guest token | no | implemented |
| POST | `/api/v1/exercises` | guest token | yes | implemented |
| PUT | `/api/v1/exercises/{exercise_id}` | guest token | yes | implemented |
| DELETE | `/api/v1/exercises/{exercise_id}` | guest token | yes | implemented |
| GET | `/api/v1/workouts` | guest token | no | implemented |
| POST | `/api/v1/workouts` | guest token | yes | implemented |
| PUT | `/api/v1/workouts/{workout_id}` | guest token | yes | implemented |
| POST | `/api/v1/workouts/{workout_id}/start` | guest token | yes | implemented |
| POST | `/api/v1/workouts/{workout_id}/complete` | guest token | yes | implemented |
| POST | `/api/v1/sync/push` | guest token | yes | implemented push basis |

## Sync push

Example request:

```json
{
  "operations": [
    {
      "operation_id": "018f...",
      "entity_type": "exercise",
      "action": "UPSERT",
      "payload": {
        "id": "018e...",
        "name": "Custom exercise",
        "description": "",
        "primary_muscle_group": "Back",
        "equipment": "Resistance band",
        "tracking_type": "REPS",
        "notes": ""
      }
    }
  ]
}
```

The endpoint processes at most 100 operations per request. UUID upsert and operation/idempotency records make retries safe. Current scope is push only; pull cursors, tombstone download and conflict payloads are planned.

## Compatibility rules

- Enums are uppercase wire strings and must not be reordered as numeric ordinals.
- Timestamps are ISO-8601 UTC values.
- IDs are UUID strings.
- Unknown request fields are rejected to catch client drift early.
- Response additions are backward-compatible; removals/renames require v2.
- Provider-specific payloads do not enter public domain endpoints without normalization.

## Public exercise catalog

- `GET /api/v1/catalog/exercises?muscle={slug}&equipment={slug}` returns only reviewed published records; filters combine and unknown facets return an empty list.
- `GET /api/v1/catalog/exercises/{id}` returns public detail or 404.
- `GET /api/v1/catalog/muscles` and `/api/v1/catalog/equipment` return referenced public facets.

Responses include source, external ID, provenance, license name/URL, version, tracking type and normalized relations. Private custom exercises never appear on these routes.
