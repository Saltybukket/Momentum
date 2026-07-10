from httpx import AsyncClient
from sqlalchemy import func, select

from fitness_platform.infrastructure.orm import OutboxEventRow
from tests.conftest import create_guest


async def _create_exercise(client: AsyncClient, token: str) -> str:
    response = await client.post(
        "/api/v1/exercises",
        headers={"Authorization": f"Bearer {token}"},
        json={
            "name": "Squat",
            "primary_muscle_group": "Quadriceps",
            "equipment": "None",
            "tracking_type": "REPS",
        },
    )
    assert response.status_code == 201
    return response.json()["id"]


async def test_workout_create_start_complete_emits_event_once(app_client) -> None:
    client, container = app_client
    token, _ = await create_guest(client)
    exercise_id = await _create_exercise(client, token)
    auth = {"Authorization": f"Bearer {token}"}

    created = await client.post(
        "/api/v1/workouts",
        headers=auth,
        json={"title": "Full Body", "exercise_ids": [exercise_id]},
    )
    assert created.status_code == 201, created.text
    workout_id = created.json()["id"]

    started = await client.post(f"/api/v1/workouts/{workout_id}/start", headers=auth)
    assert started.status_code == 200
    assert started.json()["status"] == "IN_PROGRESS"

    completed = await client.post(f"/api/v1/workouts/{workout_id}/complete", headers=auth)
    repeated = await client.post(f"/api/v1/workouts/{workout_id}/complete", headers=auth)
    assert completed.status_code == repeated.status_code == 200
    assert completed.headers["Domain-Event-Emitted"] == "true"
    assert repeated.headers["Domain-Event-Emitted"] == "false"

    async with container.database.session_factory() as session:
        count = await session.scalar(
            select(func.count())
            .select_from(OutboxEventRow)
            .where(OutboxEventRow.event_type == "WorkoutCompleted")
        )
    assert count == 1
