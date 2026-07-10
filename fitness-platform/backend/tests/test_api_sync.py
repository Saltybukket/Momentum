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
