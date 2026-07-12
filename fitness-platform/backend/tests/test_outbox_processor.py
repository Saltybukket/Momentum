import asyncio
from datetime import UTC, datetime, timedelta
from uuid import UUID

from sqlalchemy import func, select, update

from fitness_platform.domain.events import (
    ExerciseCreated,
    GuestProfileCreated,
    WorkoutCompleted,
    WorkoutCreated,
    WorkoutStarted,
)
from fitness_platform.infrastructure.orm import OutboxEventRow
from tests.conftest import create_guest


async def test_post_commit_handler_failures_do_not_change_http_success(app_client) -> None:
    client, container = app_client

    async def fail(_event) -> None:
        raise RuntimeError("handler failure")

    for event_type in (
        GuestProfileCreated,
        ExerciseCreated,
        WorkoutCreated,
        WorkoutStarted,
        WorkoutCompleted,
    ):
        container.events.register(event_type, fail)

    token, _ = await create_guest(client, key="durable-http")
    auth = {"Authorization": f"Bearer {token}"}
    exercise = await client.post(
        "/api/v1/exercises",
        headers={**auth, "Idempotency-Key": "durable-exercise"},
        json={"name": "Durable"},
    )
    exercise_replay = await client.post(
        "/api/v1/exercises",
        headers={**auth, "Idempotency-Key": "durable-exercise"},
        json={"name": "Durable"},
    )
    workout = await client.post("/api/v1/workouts", headers=auth, json={"title": "Durable"})
    workout_id = workout.json()["id"]
    started = await client.post(f"/api/v1/workouts/{workout_id}/start", headers=auth)
    completed = await client.post(f"/api/v1/workouts/{workout_id}/complete", headers=auth)
    assert [
        exercise.status_code,
        workout.status_code,
        started.status_code,
        completed.status_code,
    ] == [
        201,
        201,
        200,
        200,
    ]
    assert exercise_replay.status_code == 201
    assert exercise_replay.headers["Idempotency-Replayed"] == "true"
    async with container.database.session_factory() as session:
        assert await session.scalar(select(func.count()).select_from(OutboxEventRow)) == 5


async def test_outbox_retries_then_processes_once(app_client) -> None:
    client, container = app_client
    token, _ = await create_guest(client, key="outbox-retry")
    calls: list[UUID] = []

    async def fail_once(event: ExerciseCreated) -> None:
        calls.append(event.event_id)
        if len(calls) == 1:
            raise RuntimeError("transient secret detail")

    container.events.register(ExerciseCreated, fail_once)
    created = await client.post(
        "/api/v1/exercises",
        headers={"Authorization": f"Bearer {token}"},
        json={"name": "Retry"},
    )
    assert created.status_code == 201
    first = await container.outbox_processor.process("worker-a", 10)
    assert first["failed"] == 1
    async with container.database.session_factory() as session:
        row = (
            await session.execute(
                select(OutboxEventRow).where(OutboxEventRow.event_type == "ExerciseCreated")
            )
        ).scalar_one()
        assert row.last_error == "RuntimeError"
        await session.execute(
            update(OutboxEventRow)
            .where(OutboxEventRow.event_id == row.event_id)
            .values(next_attempt_at=datetime.now(UTC) - timedelta(seconds=1))
        )
        await session.commit()
    second = await container.outbox_processor.process("worker-b", 10)
    assert second["processed"] == 1
    assert len(calls) == 2
    assert calls[0] == calls[1]


async def test_outbox_dead_letters_permanent_failure(app_client) -> None:
    client, container = app_client
    token, _ = await create_guest(client, key="outbox-dead")

    async def fail(_event: ExerciseCreated) -> None:
        raise RuntimeError("never delivered")

    container.events.register(ExerciseCreated, fail)
    await client.post(
        "/api/v1/exercises",
        headers={"Authorization": f"Bearer {token}"},
        json={"name": "Dead"},
    )
    async with container.database.session_factory() as session:
        await session.execute(
            update(OutboxEventRow)
            .where(OutboxEventRow.event_type == "ExerciseCreated")
            .values(max_attempts=1)
        )
        await session.commit()
    report = await container.outbox_processor.process("worker-dead", 10)
    assert report["dead_letter"] == 1


async def test_unknown_event_dead_letters_and_stale_lease_is_reclaimed(app_client) -> None:
    client, container = app_client
    token, _ = await create_guest(client, key="outbox-stale")
    await client.post(
        "/api/v1/exercises",
        headers={"Authorization": f"Bearer {token}"},
        json={"name": "Unknown"},
    )
    async with container.database.session_factory() as session:
        await session.execute(
            update(OutboxEventRow)
            .where(OutboxEventRow.event_type == "ExerciseCreated")
            .values(
                event_type="UnknownEvent",
                status="PROCESSING",
                claim_owner="crashed-worker",
                lease_expires_at=datetime.now(UTC) - timedelta(seconds=1),
                next_attempt_at=datetime.now(UTC) - timedelta(days=1),
                max_attempts=1,
            )
        )
        await session.commit()
    report = await container.outbox_processor.process("recovery-worker", 1)
    assert report == {"claimed": 1, "processed": 0, "failed": 0, "dead_letter": 1}


async def test_outbox_batch_limit_is_enforced(app_client) -> None:
    client, container = app_client
    token, _ = await create_guest(client, key="outbox-limit")
    for index in range(3):
        await client.post(
            "/api/v1/exercises",
            headers={"Authorization": f"Bearer {token}"},
            json={"name": f"Limited {index}"},
        )
    report = await container.outbox_processor.process("limited-worker", 1)
    assert report["claimed"] == report["processed"] == 1


async def _assert_parallel_workers_claim_exclusively(app_client) -> None:
    client, container = app_client
    token, _ = await create_guest(client, key="outbox-parallel")
    delivered: list[UUID] = []

    async def record(event: ExerciseCreated) -> None:
        delivered.append(event.event_id)

    container.events.register(ExerciseCreated, record)
    for index in range(8):
        await client.post(
            "/api/v1/exercises",
            headers={"Authorization": f"Bearer {token}"},
            json={"name": f"Parallel {index}"},
        )
    reports = await asyncio.gather(
        container.outbox_processor.process("worker-one", 20),
        container.outbox_processor.process("worker-two", 20),
    )
    assert sum(report["processed"] for report in reports) == 9
    assert len(delivered) == len(set(delivered)) == 8


async def test_parallel_workers_claim_exclusively_on_postgresql(postgres_app_client) -> None:
    await _assert_parallel_workers_claim_exclusively(postgres_app_client)
