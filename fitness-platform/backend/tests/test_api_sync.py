from uuid import uuid4

from tests.conftest import create_guest


async def test_push_sync_persists_exercise_and_is_idempotent(app_client) -> None:
    client, _ = app_client
    token, _ = await create_guest(client)
    exercise_id = str(uuid4())
    operation_id = str(uuid4())
    headers = {
        "Authorization": f"Bearer {token}",
        "Idempotency-Key": "sync-batch-1",
    }
    payload = {
        "operations": [
            {
                "operation_id": operation_id,
                "entity_type": "exercise",
                "action": "UPSERT",
                "payload": {
                    "id": exercise_id,
                    "name": "Offline Exercise",
                    "description": "Created locally",
                    "primary_muscle_group": "Core",
                    "equipment": "None",
                    "tracking_type": "REPS",
                    "notes": "",
                },
            }
        ]
    }

    first = await client.post("/api/v1/sync/push", headers=headers, json=payload)
    second = await client.post("/api/v1/sync/push", headers=headers, json=payload)
    assert first.status_code == second.status_code == 200, first.text
    assert second.headers["Idempotency-Replayed"] == "true"

    listed = await client.get("/api/v1/exercises", headers={"Authorization": f"Bearer {token}"})
    assert listed.json()["page"]["total"] == 1
    assert listed.json()["items"][0]["id"] == exercise_id


async def test_exercise_pull_returns_tombstone_and_conflict(app_client) -> None:
    client, _ = app_client
    token, _ = await create_guest(client)
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": "sync-feed"}
    exercise_id, first_operation = str(uuid4()), str(uuid4())
    payload = {
        "operations": [
            {
                "operation_id": first_operation,
                "entity_type": "exercise",
                "action": "UPSERT",
                "payload": {
                    "id": exercise_id,
                    "name": "Private",
                    "tracking_type": "REPS",
                    "base_revision": None,
                },
            }
        ]
    }
    created = await client.post("/api/v1/sync/push", headers=headers, json=payload)
    assert created.status_code == 200, created.text
    assert created.json()["results"][0]["revision"] == 1

    stale = await client.post(
        "/api/v1/sync/push",
        headers={**headers, "Idempotency-Key": "stale"},
        json={
            "operations": [
                {
                    "operation_id": str(uuid4()),
                    "entity_type": "exercise",
                    "action": "UPSERT",
                    "payload": {
                        "id": exercise_id,
                        "name": "Stale",
                        "tracking_type": "REPS",
                        "base_revision": 0,
                    },
                }
            ]
        },
    )
    assert stale.status_code == 200
    assert stale.json()["results"][0]["status"] == "CONFLICT"
    remote = stale.json()["results"][0]["remote_exercise"]
    assert remote["id"] == exercise_id
    assert remote["revision"] == 1
    assert remote["name"] == "Private"

    deleted = await client.delete(
        f"/api/v1/exercises/{exercise_id}", headers={"Authorization": f"Bearer {token}"}
    )
    assert deleted.status_code == 204
    pulled = await client.get(
        "/api/v1/sync/exercises?cursor=0&limit=10", headers={"Authorization": f"Bearer {token}"}
    )
    assert pulled.status_code == 200, pulled.text
    changes = pulled.json()["changes"]
    assert len(changes) == 2
    assert changes[-1]["deleted"] is True
    assert changes[-1]["exercise"]["revision"] == 2
