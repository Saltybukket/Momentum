"""Backend-only HTTP smoke flow for the architecture scaffold.

Start the backend first, then run `python scripts/smoke_test.py`. Set FITNESS_SMOKE_BASE_URL to
use a non-default host or port. The Android-local half is covered separately by Room and
repository tests.
"""

from __future__ import annotations

import json
import os
import urllib.request
from typing import Any
from uuid import uuid4

BASE_URL = os.getenv("FITNESS_SMOKE_BASE_URL", "http://localhost:8000").rstrip("/")
if not BASE_URL.startswith(("http://", "https://")):
    raise ValueError("FITNESS_SMOKE_BASE_URL must use http:// or https://")


def request(
    method: str,
    path: str,
    payload: dict[str, Any] | None = None,
    token: str | None = None,
    idempotency_key: str | None = None,
) -> dict[str, Any]:
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Content-Type": "application/json", "X-Request-ID": str(uuid4())}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if idempotency_key:
        headers["Idempotency-Key"] = idempotency_key
    http_request = urllib.request.Request(  # noqa: S310 - URL scheme validated above
        BASE_URL + path,
        data=data,
        headers=headers,
        method=method,
    )
    with urllib.request.urlopen(http_request, timeout=10) as response:  # noqa: S310
        result: dict[str, Any] = json.load(response)
        return result


def main() -> None:
    session = request(
        "POST",
        "/api/v1/guest-sessions",
        {
            "display_name": "Smoke Guest",
            "installation_id": str(uuid4()),
            "recovery_secret": f"smoke-recovery-{uuid4()}-{uuid4()}",
        },
        idempotency_key=str(uuid4()),
    )
    token = str(session["guest_token"])
    exercise_id = str(uuid4())
    exercise = request(
        "POST",
        "/api/v1/exercises",
        {
            "id": exercise_id,
            "name": "Smoke Exercise",
            "description": "E2E demo",
            "primary_muscle_group": "Full body",
            "equipment": "None",
            "tracking_type": "REPS",
            "notes": "DEMO DATA",
        },
        token,
        str(uuid4()),
    )
    workout = request(
        "POST",
        "/api/v1/workouts",
        {
            "title": "Smoke Workout",
            "exercise_ids": [exercise["id"]],
            "notes": "DEMO DATA",
        },
        token,
        str(uuid4()),
    )
    request(
        "POST",
        f"/api/v1/workouts/{workout['id']}/start",
        {},
        token,
        str(uuid4()),
    )
    completed = request(
        "POST",
        f"/api/v1/workouts/{workout['id']}/complete",
        {},
        token,
        str(uuid4()),
    )
    if completed["status"] != "COMPLETED":
        raise RuntimeError(f"Expected COMPLETED workout, got {completed['status']!r}")
    print("Smoke flow passed:", completed["id"])


if __name__ == "__main__":
    main()
