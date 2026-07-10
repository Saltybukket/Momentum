from uuid import uuid4

from httpx import AsyncClient

from tests.conftest import create_guest


async def test_exercise_crud_and_validation(app_client: tuple[AsyncClient, object]) -> None:
    client, _ = app_client
    token, _ = await create_guest(client)
    auth = {"Authorization": f"Bearer {token}"}

    invalid = await client.post(
        "/api/v1/exercises",
        headers=auth,
        json={"name": "   ", "primary_muscle_group": "Back", "equipment": "Band"},
    )
    assert invalid.status_code == 422

    exercise_id = str(uuid4())
    created = await client.post(
        "/api/v1/exercises",
        headers={**auth, "Idempotency-Key": "exercise-create"},
        json={
            "id": exercise_id,
            "name": "Band Row",
            "description": "Demo movement",
            "primary_muscle_group": "Back",
            "equipment": "Resistance band",
            "tracking_type": "REPS",
            "notes": "Keep neutral spine",
        },
    )
    assert created.status_code == 201, created.text
    assert created.json()["id"] == exercise_id

    updated = await client.put(
        f"/api/v1/exercises/{exercise_id}",
        headers=auth,
        json={
            "name": "Seated Band Row",
            "description": "Updated",
            "primary_muscle_group": "Back",
            "equipment": "Resistance band",
            "tracking_type": "REPS",
            "notes": "",
        },
    )
    assert updated.status_code == 200
    assert updated.json()["name"] == "Seated Band Row"

    listed = await client.get("/api/v1/exercises", headers=auth)
    assert listed.status_code == 200
    assert listed.json()["page"]["total"] == 1

    deleted = await client.delete(f"/api/v1/exercises/{exercise_id}", headers=auth)
    assert deleted.status_code == 204
    listed_after = await client.get("/api/v1/exercises", headers=auth)
    assert listed_after.json()["page"]["total"] == 0


async def test_repeated_idempotency_key_does_not_duplicate_exercise(
    app_client: tuple[AsyncClient, object],
) -> None:
    client, _ = app_client
    token, _ = await create_guest(client)
    headers = {
        "Authorization": f"Bearer {token}",
        "Idempotency-Key": "exercise-replay",
    }
    payload = {
        "name": "Push-up",
        "primary_muscle_group": "Chest",
        "equipment": "None",
        "tracking_type": "REPS",
    }
    first = await client.post("/api/v1/exercises", headers=headers, json=payload)
    second = await client.post("/api/v1/exercises", headers=headers, json=payload)
    listed = await client.get("/api/v1/exercises", headers={"Authorization": f"Bearer {token}"})

    assert first.status_code == second.status_code == 201
    assert first.json()["id"] == second.json()["id"]
    assert listed.json()["page"]["total"] == 1
