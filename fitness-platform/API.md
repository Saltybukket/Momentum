# API conventions

## Base URL and versioning

All product routes use `/api/v1/`. The unversioned `GET /health` route is operational. Breaking changes require a new major route prefix; additive schema fields may be introduced within v1 because clients must ignore unknown response fields.

Interactive OpenAPI is available at `/docs` only when `FITNESS_ENABLE_DOCS=true`. The contract can be exported to `shared/openapi.json` with `make openapi`.

## Authentication foundation

`POST /api/v1/guest-sessions` returns a random bearer token. Only a SHA-256-derived token hash is stored. A stable installation ID plus recovery secret can renew a rejected/expired bearer without replacing ownership; a rejected proof enters an explicit blocked state instead of looping or deleting local data. This is still **development-only**: it has no production identity proof, account-link UI or revocation/session-management surface. Future Google Credential Manager and email identities map to the same internal user/profile ownership model.

Authenticated request:

```http
Authorization: Bearer <development-guest-token>
```

## Request IDs

Clients may send `X-Request-ID` using the ASCII allowlist `[A-Za-z0-9._:-]` with at most 64 characters. Invalid or oversized values are replaced by a server UUID and are neither reflected nor logged. Every response, including unexpected `500`, returns the normalized request ID.

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

Unexpected errors always use code `INTERNAL_ERROR`, message `An internal error occurred.`, an empty details list and the normalized request ID. Logs record the exception type and context without reflecting exception messages that may contain secrets.

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
| GET | `/api/v1/sync/exercises` | guest token | no | cursor pull with tombstones |
| GET | `/api/v1/catalog/exercises` | no | no | active release page |
| GET | `/api/v1/catalog/exercises/{exercise_id}` | no | no | active release detail |
| GET | `/api/v1/catalog/snapshot` | no | no | complete active release |
| GET | `/api/v1/catalog/muscles` | no | no | active release facets |
| GET | `/api/v1/catalog/equipment` | no | no | active release facets |

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

The endpoint processes at most 100 operations per request. UUID upsert and operation/idempotency
records make retries safe. Exercise revision conflicts return the authoritative remote exercise
snapshot. `GET /api/v1/sync/exercises?cursor={revision}` supplies ordered private-exercise changes,
including tombstones, plus the next cursor; Android commits each page and its cursor atomically in
Room. Profiles and workouts remain push-oriented in the current client.

## Compatibility rules

- Enums are uppercase wire strings and must not be reordered as numeric ordinals.
- Timestamps are ISO-8601 UTC values.
- IDs are UUID strings.
- Unknown request fields are rejected to catch client drift early.
- Response additions are backward-compatible; removals/renames require v2.
- Provider-specific payloads do not enter public domain endpoints without normalization.

## Public exercise catalog

- `GET /api/v1/catalog/exercises?muscle={slug}&equipment={slug}&q={literal}` returns only reviewed published records from one active release; filters combine, `%`/`_` are literal search characters and unknown facets return an empty list. Pages include `catalog_version` and `content_hash`.
- `GET /api/v1/catalog/exercises/{id}` returns public detail or 404.
- `GET /api/v1/catalog/muscles` and `/api/v1/catalog/equipment` return referenced public facets.
- `GET /api/v1/catalog/snapshot` returns the complete manifest and content from the same active release. `ETag` is derived from its SHA-256 content hash and matching `If-None-Match` returns `304`.

Responses include source, external ID, provenance, license name/URL, version, tracking type and normalized relations. Private custom exercises never appear on these routes.

Catalog imports validate the full JSON document before writing. A newer release is staged and
activated atomically; an older release is retained without replacing current content. Identical
version/hash imports report `UNCHANGED`, while version/hash/batch conflicts fail. Operators can
activate a retained version explicitly with:

```bash
cd backend
uv run python -m fitness_platform.catalog_import --activate-version VERSION
```
