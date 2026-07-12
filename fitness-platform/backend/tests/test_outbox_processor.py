import asyncio
from datetime import UTC, datetime, timedelta
from typing import cast
from uuid import UUID, uuid4

import pytest
from sqlalchemy import func, select, update

from fitness_platform.application.outbox import OutboxProcessor
from fitness_platform.core.clock import SystemClock
from fitness_platform.core.ids import RandomUuidProvider
from fitness_platform.domain.events import (
    ExerciseCreated,
    GuestProfileCreated,
    WorkoutCompleted,
    WorkoutCreated,
    WorkoutStarted,
)
from fitness_platform.domain.ports import UnitOfWork
from fitness_platform.infrastructure.orm import OutboxEventRow
from fitness_platform.infrastructure.uow import SqlAlchemyUnitOfWork
from tests.conftest import create_guest


def _processor(
    container,
    *,
    lease_seconds: float = 0.15,
    heartbeat_seconds: float = 0.02,
    noop_event_types: frozenset[str] | None = None,
) -> OutboxProcessor:
    def uow_factory() -> UnitOfWork:
        return cast(UnitOfWork, SqlAlchemyUnitOfWork(container.database.session_factory))

    kwargs = {}
    if noop_event_types is not None:
        kwargs["noop_event_types"] = noop_event_types
    return OutboxProcessor(
        uow_factory,
        SystemClock(),
        RandomUuidProvider(),
        container.events,
        lease_duration=timedelta(seconds=lease_seconds),
        heartbeat_interval_seconds=heartbeat_seconds,
        **kwargs,
    )


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
    assert report == {
        "claimed": 1,
        "processed": 0,
        "failed": 0,
        "dead_letter": 1,
        "lost_claim": 0,
    }


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


async def _assert_heartbeat_prevents_active_reclaim(app_client) -> None:
    client, container = app_client
    token, _ = await create_guest(client, key="heartbeat")
    await container.outbox_processor.process("drain-guest", 10)
    started = asyncio.Event()
    release = asyncio.Event()
    calls: list[UUID] = []

    async def slow_handler(event: ExerciseCreated) -> None:
        calls.append(event.event_id)
        started.set()
        await release.wait()

    container.events.register(ExerciseCreated, slow_handler)
    await client.post(
        "/api/v1/exercises",
        headers={"Authorization": f"Bearer {token}"},
        json={"name": "Heartbeat"},
    )
    first_processor = _processor(container)
    second_processor = _processor(container)
    first = asyncio.create_task(first_processor.process("same-worker", 1))
    await asyncio.wait_for(started.wait(), timeout=1)
    await asyncio.sleep(0.25)
    second = await second_processor.process("same-worker", 1)
    release.set()
    first_report = await asyncio.wait_for(first, timeout=1)
    assert second["claimed"] == 0
    assert first_report["processed"] == 1
    assert len(calls) == 1


async def test_heartbeat_prevents_active_reclaim_on_sqlite(app_client) -> None:
    await _assert_heartbeat_prevents_active_reclaim(app_client)


async def test_heartbeat_prevents_active_reclaim_on_postgresql(postgres_app_client) -> None:
    await _assert_heartbeat_prevents_active_reclaim(postgres_app_client)


async def test_stale_claim_token_cannot_complete_or_increment_attempts(app_client) -> None:
    client, container = app_client
    token, _ = await create_guest(client, key="claim-token")
    await container.outbox_processor.process("drain-token-guest", 10)
    await client.post(
        "/api/v1/exercises",
        headers={"Authorization": f"Bearer {token}"},
        json={"name": "Claim token"},
    )
    old_token = uuid4()
    new_token = uuid4()
    async with SqlAlchemyUnitOfWork(container.database.session_factory) as uow:
        records = await uow.outbox.claim_due(
            worker_id="same-worker",
            claim_token=old_token,
            now=datetime.now(UTC),
            lease_expires_at=datetime.now(UTC) + timedelta(minutes=5),
            limit=1,
        )
        await uow.commit()
    event_id = records[0].event_id
    async with container.database.session_factory() as session:
        await session.execute(
            update(OutboxEventRow)
            .where(OutboxEventRow.event_id == event_id)
            .values(claim_token=new_token)
        )
        await session.commit()
    async with SqlAlchemyUnitOfWork(container.database.session_factory) as uow:
        assert not await uow.outbox.mark_processed(
            event_id, "same-worker", old_token, datetime.now(UTC)
        )
        assert (
            await uow.outbox.mark_failed(
                event_id, "same-worker", old_token, datetime.now(UTC), "RuntimeError"
            )
            == "LOST_CLAIM"
        )
        await uow.commit()
    async with container.database.session_factory() as session:
        row = await session.get(OutboxEventRow, event_id)
        assert row is not None
        assert row.status == "PROCESSING"
        assert row.claim_token == new_token
        assert row.attempts == 0


async def test_processor_reports_lost_claim_without_failure_mutation(app_client) -> None:
    client, container = app_client
    token, _ = await create_guest(client, key="lost-claim-report")
    await container.outbox_processor.process("drain-lost-guest", 10)
    started = asyncio.Event()
    release = asyncio.Event()

    async def handler(_event: ExerciseCreated) -> None:
        started.set()
        await release.wait()

    container.events.register(ExerciseCreated, handler)
    await client.post(
        "/api/v1/exercises",
        headers={"Authorization": f"Bearer {token}"},
        json={"name": "Lost claim report"},
    )
    processor = _processor(container)
    task = asyncio.create_task(processor.process("shared-worker", 1))
    await asyncio.wait_for(started.wait(), timeout=1)
    replacement_token = uuid4()
    async with container.database.session_factory() as session:
        await session.execute(
            update(OutboxEventRow)
            .where(OutboxEventRow.event_type == "ExerciseCreated")
            .values(claim_token=replacement_token)
        )
        await session.commit()
    release.set()
    report = await asyncio.wait_for(task, timeout=1)
    assert report["lost_claim"] == 1
    assert report["processed"] == report["failed"] == 0
    async with container.database.session_factory() as session:
        row = (
            await session.execute(
                select(OutboxEventRow).where(OutboxEventRow.event_type == "ExerciseCreated")
            )
        ).scalar_one()
        assert row.status == "PROCESSING"
        assert row.claim_token == replacement_token
        assert row.attempts == 0


async def test_known_event_without_handler_requires_explicit_noop(app_client) -> None:
    client, container = app_client
    token, _ = await create_guest(client, key="missing-handler")
    await container.outbox_processor.process("drain-handler-guest", 10)
    await client.post(
        "/api/v1/exercises",
        headers={"Authorization": f"Bearer {token}"},
        json={"name": "Missing handler"},
    )
    async with container.database.session_factory() as session:
        await session.execute(
            update(OutboxEventRow)
            .where(OutboxEventRow.event_type == "ExerciseCreated")
            .values(max_attempts=1)
        )
        await session.commit()
    report = await _processor(container, noop_event_types=frozenset()).process(
        "missing-handler-worker", 1
    )
    assert report["dead_letter"] == 1


async def test_cancelled_handler_stops_heartbeat_and_stale_lease_reclaims(app_client) -> None:
    client, container = app_client
    token, _ = await create_guest(client, key="cancel-heartbeat")
    await container.outbox_processor.process("drain-cancel-guest", 10)
    started = asyncio.Event()
    release = asyncio.Event()

    async def cancellable(_event: ExerciseCreated) -> None:
        started.set()
        await release.wait()

    container.events.register(ExerciseCreated, cancellable)
    await client.post(
        "/api/v1/exercises",
        headers={"Authorization": f"Bearer {token}"},
        json={"name": "Cancelled heartbeat"},
    )
    processor = _processor(container, lease_seconds=0.08, heartbeat_seconds=0.02)
    task = asyncio.create_task(processor.process("cancelled-worker", 1))
    await asyncio.wait_for(started.wait(), timeout=1)
    task.cancel()
    with pytest.raises(asyncio.CancelledError):
        await task
    await asyncio.sleep(0.1)
    release.set()
    reclaimed = await _processor(container).process("recovery-worker", 1)
    assert reclaimed["claimed"] == reclaimed["processed"] == 1


async def test_worker_id_rejects_control_characters(app_client) -> None:
    _, container = app_client
    with pytest.raises(ValueError, match="safe ASCII"):
        await container.outbox_processor.process("worker\nlog-injection", 1)
