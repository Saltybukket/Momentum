import asyncio
from datetime import UTC, datetime, timedelta
from uuid import uuid4

from httpx import AsyncClient
from sqlalchemy import func, select, update

from fitness_platform.container import AppContainer
from fitness_platform.infrastructure.orm import ExerciseRow, IdempotencyRecordRow
from tests.conftest import create_guest


async def test_parallel_exercise_key_commits_one_operation(
    app_client: tuple[AsyncClient, AppContainer],
) -> None:
    client, container = app_client
    token, _ = await create_guest(client)
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": "parallel-exercise"}
    payload = {"name": "Parallel", "tracking_type": "REPS"}
    responses = await asyncio.gather(
        *(client.post("/api/v1/exercises", headers=headers, json=payload) for _ in range(12))
    )
    assert all(response.status_code in {201, 409} for response in responses)
    assert sum(response.status_code == 201 for response in responses) >= 1
    async with container.database.session_factory() as session:
        count = await session.scalar(select(func.count()).select_from(ExerciseRow))
    assert count == 1


async def test_expired_key_is_reusable_and_old_response_is_not_replayed(
    app_client: tuple[AsyncClient, AppContainer],
) -> None:
    client, container = app_client
    token, _ = await create_guest(client)
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": "expiring-key"}
    first = await client.post("/api/v1/exercises", headers=headers, json={"name": "First"})
    assert first.status_code == 201
    async with container.database.session_factory() as session:
        await session.execute(
            update(IdempotencyRecordRow)
            .where(IdempotencyRecordRow.key == "expiring-key")
            .values(expires_at=datetime.now(UTC) - timedelta(seconds=1))
        )
        await session.commit()
    second = await client.post("/api/v1/exercises", headers=headers, json={"name": "Second"})
    assert second.status_code == 201
    assert second.json()["id"] != first.json()["id"]


async def test_cross_user_client_uuid_returns_controlled_conflict(app_client) -> None:
    client, _ = app_client
    token_a, _ = await create_guest(client, key="collision-a")
    token_b, _ = await create_guest(client, key="collision-b")
    exercise_id = str(uuid4())
    payload = {"id": exercise_id, "name": "Owned", "tracking_type": "REPS"}
    first = await client.post(
        "/api/v1/exercises",
        headers={"Authorization": f"Bearer {token_a}", "Idempotency-Key": "owner-a"},
        json=payload,
    )
    second = await client.post(
        "/api/v1/exercises",
        headers={"Authorization": f"Bearer {token_b}", "Idempotency-Key": "owner-b"},
        json=payload,
    )
    assert first.status_code == 201
    assert second.status_code == 409
    assert second.json()["error"]["code"] == "CONFLICT"
    assert "owner" not in second.text.lower()


async def test_idempotency_key_validation_is_bounded(app_client) -> None:
    client, _ = app_client
    token, _ = await create_guest(client)
    response = await client.post(
        "/api/v1/exercises",
        headers={"Authorization": f"Bearer {token}", "Idempotency-Key": "x" * 129},
        json={"name": "Rejected"},
    )
    assert response.status_code == 409
