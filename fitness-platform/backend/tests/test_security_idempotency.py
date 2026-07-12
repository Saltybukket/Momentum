import asyncio
from datetime import UTC, datetime, timedelta
from uuid import uuid4

import pytest
from httpx import AsyncClient
from sqlalchemy import func, select, update

from fitness_platform.application.idempotency import IdempotencyService
from fitness_platform.container import AppContainer
from fitness_platform.infrastructure.orm import (
    ExerciseRow,
    IdempotencyRecordRow,
    OutboxEventRow,
)
from fitness_platform.infrastructure.repositories import SqlAlchemyIdempotencyRepository
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
    token, profile = await create_guest(client)
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": "expiring-key"}
    first = await client.post("/api/v1/exercises", headers=headers, json={"name": "First"})
    assert first.status_code == 201
    async with container.database.session_factory() as session:
        await session.execute(
            update(IdempotencyRecordRow)
            .where(
                IdempotencyRecordRow.key
                == IdempotencyService.storage_key(
                    f"POST:/api/v1/exercises:principal={profile['user_id']}", "expiring-key"
                )
            )
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


async def test_idempotency_key_rejects_changed_payload(app_client) -> None:
    client, _ = app_client
    token, _ = await create_guest(client)
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": "same-key"}
    first = await client.post("/api/v1/exercises", headers=headers, json={"name": "First"})
    changed = await client.post("/api/v1/exercises", headers=headers, json={"name": "Changed"})
    assert first.status_code == 201
    assert changed.status_code == 409
    assert changed.json()["error"]["code"] == "CONFLICT"


async def test_failed_idempotency_record_returns_controlled_conflict(
    app_client: tuple[AsyncClient, AppContainer],
) -> None:
    client, container = app_client
    token, profile = await create_guest(client)
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": "failed-key"}
    payload = {"name": "Failure"}
    first = await client.post("/api/v1/exercises", headers=headers, json=payload)
    scope = f"POST:/api/v1/exercises:principal={profile['user_id']}"
    async with container.database.session_factory() as session:
        await session.execute(
            update(IdempotencyRecordRow)
            .where(
                IdempotencyRecordRow.scope == scope,
                IdempotencyRecordRow.key == IdempotencyService.storage_key(scope, "failed-key"),
            )
            .values(state="FAILED")
        )
        await session.commit()
    retried = await client.post("/api/v1/exercises", headers=headers, json=payload)
    assert first.status_code == 201
    assert retried.status_code == 409


async def test_idempotency_key_is_hashed_at_rest(
    app_client: tuple[AsyncClient, AppContainer],
) -> None:
    client, container = app_client
    token, _ = await create_guest(client)
    await client.post(
        "/api/v1/exercises",
        headers={"Authorization": f"Bearer {token}", "Idempotency-Key": "private-key"},
        json={"name": "Hashed"},
    )
    async with container.database.session_factory() as session:
        stored = (await session.execute(select(IdempotencyRecordRow.key))).scalar_one()
    assert stored != "private-key"
    assert len(stored) == 64


async def test_completion_failure_rolls_back_domain_mutation(
    app_client: tuple[AsyncClient, AppContainer], monkeypatch: pytest.MonkeyPatch
) -> None:
    client, container = app_client
    token, _ = await create_guest(client)

    async def fail_completion(*args, **kwargs) -> None:
        raise RuntimeError("simulated crash before completion")

    monkeypatch.setattr(SqlAlchemyIdempotencyRepository, "complete", fail_completion)
    with pytest.raises(RuntimeError, match="simulated crash"):
        await client.post(
            "/api/v1/exercises",
            headers={"Authorization": f"Bearer {token}", "Idempotency-Key": "crash-gap"},
            json={"name": "Must Roll Back"},
        )
    async with container.database.session_factory() as session:
        exercises = await session.scalar(select(func.count()).select_from(ExerciseRow))
        records = await session.scalar(select(func.count()).select_from(IdempotencyRecordRow))
        outbox = await session.scalar(select(func.count()).select_from(OutboxEventRow))
    assert exercises == 0
    assert records == 0
    assert outbox == 1  # guest-profile creation only


async def test_cleanup_expired_is_bounded(app_client: tuple[AsyncClient, AppContainer]) -> None:
    client, container = app_client
    token, _ = await create_guest(client)
    for key in ("cleanup-a", "cleanup-b"):
        await client.post(
            "/api/v1/exercises",
            headers={"Authorization": f"Bearer {token}", "Idempotency-Key": key},
            json={"name": key},
        )
    async with container.database.session_factory() as session:
        await session.execute(
            update(IdempotencyRecordRow).values(expires_at=datetime.now(UTC) - timedelta(seconds=1))
        )
        await session.commit()
    assert await container.idempotency.cleanup_expired(1) == 1
    assert await container.idempotency.cleanup_expired(1) == 1
    assert await container.idempotency.cleanup_expired(1) == 0
    with pytest.raises(ValueError, match="batch_limit"):
        await container.idempotency.cleanup_expired(0)


async def test_parallel_exercise_key_is_atomic_on_postgresql(
    postgres_app_client: tuple[AsyncClient, AppContainer],
) -> None:
    await test_parallel_exercise_key_commits_one_operation(postgres_app_client)
