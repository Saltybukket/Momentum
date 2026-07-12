from uuid import UUID

from httpx import AsyncClient
from sqlalchemy import func, select, update

from fitness_platform.infrastructure.orm import OutboxEventRow, WorkoutRow
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


async def _assert_workout_links_update_safely(app_client) -> None:
    client, _ = app_client
    token, _ = await create_guest(client)
    first_id = await _create_exercise(client, token)
    second_id = await _create_exercise(client, token)
    auth = {"Authorization": f"Bearer {token}"}
    created = await client.post(
        "/api/v1/workouts",
        headers=auth,
        json={"title": "Links", "exercise_ids": [first_id]},
    )
    workout_id = created.json()["id"]
    for exercise_ids in (
        [first_id],
        [first_id, second_id],
        [second_id, first_id],
        [second_id],
        [],
    ):
        updated = await client.put(
            f"/api/v1/workouts/{workout_id}",
            headers=auth,
            json={"title": "Links", "exercise_ids": exercise_ids},
        )
        assert updated.status_code == 200, updated.text
        assert [item["exercise_id"] for item in updated.json()["exercises"]] == exercise_ids

    duplicate = await client.put(
        f"/api/v1/workouts/{workout_id}",
        headers=auth,
        json={"title": "Links", "exercise_ids": [first_id, first_id]},
    )
    too_many = await client.put(
        f"/api/v1/workouts/{workout_id}",
        headers=auth,
        json={"title": "Links", "exercise_ids": [first_id] * 51},
    )
    status_bypass = await client.put(
        f"/api/v1/workouts/{workout_id}",
        headers=auth,
        json={"title": "Links", "exercise_ids": [], "status": "COMPLETED"},
    )
    assert duplicate.status_code == too_many.status_code == status_bypass.status_code == 422


async def test_workout_links_update_safely_on_sqlite(app_client) -> None:
    await _assert_workout_links_update_safely(app_client)


async def test_workout_links_update_safely_on_postgresql(postgres_app_client) -> None:
    await _assert_workout_links_update_safely(postgres_app_client)


async def test_workout_completion_requires_start_and_is_terminal(app_client) -> None:
    client, _ = app_client
    token, _ = await create_guest(client)
    auth = {"Authorization": f"Bearer {token}"}
    created = await client.post("/api/v1/workouts", headers=auth, json={"title": "Lifecycle"})
    workout_id = created.json()["id"]
    premature = await client.post(f"/api/v1/workouts/{workout_id}/complete", headers=auth)
    assert premature.status_code == 409
    await client.post(f"/api/v1/workouts/{workout_id}/start", headers=auth)
    completed = await client.post(f"/api/v1/workouts/{workout_id}/complete", headers=auth)
    assert completed.status_code == 200
    assert completed.json()["start_time"] is not None
    assert completed.json()["end_time"] is not None
    edit = await client.put(
        f"/api/v1/workouts/{workout_id}",
        headers=auth,
        json={"title": "Cannot edit", "exercise_ids": []},
    )
    restart = await client.post(f"/api/v1/workouts/{workout_id}/start", headers=auth)
    assert edit.status_code == restart.status_code == 409


async def _assert_cancelled_workout_is_terminal(app_client) -> None:
    client, container = app_client
    token, _ = await create_guest(client)
    auth = {"Authorization": f"Bearer {token}"}
    created = await client.post(
        "/api/v1/workouts", headers=auth, json={"title": "Cancelled fixture"}
    )
    workout_id = created.json()["id"]
    async with container.database.session_factory() as session:
        await session.execute(
            update(WorkoutRow).where(WorkoutRow.id == UUID(workout_id)).values(status="CANCELLED")
        )
        await session.commit()

    started = await client.post(f"/api/v1/workouts/{workout_id}/start", headers=auth)
    edited = await client.put(
        f"/api/v1/workouts/{workout_id}",
        headers=auth,
        json={"title": "Must remain terminal"},
    )
    assert started.status_code == edited.status_code == 409


async def test_cancelled_workout_is_terminal_on_sqlite(app_client) -> None:
    await _assert_cancelled_workout_is_terminal(app_client)


async def test_cancelled_workout_is_terminal_on_postgresql(postgres_app_client) -> None:
    await _assert_cancelled_workout_is_terminal(postgres_app_client)
