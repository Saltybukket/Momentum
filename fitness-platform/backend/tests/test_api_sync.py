import asyncio
from datetime import UTC, datetime, timedelta
from uuid import UUID, uuid4

import pytest
from sqlalchemy import func, select, update

from fitness_platform.domain.sync import SyncCommand, WorkoutStartPayload, canonical_request_hash
from fitness_platform.infrastructure.orm import (
    ProcessedSyncOperationRow,
    WorkoutExerciseRow,
    WorkoutRow,
)
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
                    "base_revision": None,
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
                    "description": "",
                    "primary_muscle_group": "Unspecified",
                    "equipment": "None",
                    "tracking_type": "REPS",
                    "notes": "",
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
                        "description": "",
                        "primary_muscle_group": "Unspecified",
                        "equipment": "None",
                        "tracking_type": "REPS",
                        "notes": "",
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


@pytest.mark.parametrize(
    "operation",
    [
        {"entity_type": "exercise", "action": "UPSERT", "payload": {"name": "Missing"}},
        {
            "entity_type": "exercise",
            "action": "UPSERT",
            "payload": {
                "id": "not-a-uuid",
                "name": "Bad",
                "description": "",
                "primary_muscle_group": "Core",
                "equipment": "None",
                "tracking_type": "INVALID",
                "notes": "",
                "base_revision": None,
            },
        },
        {
            "entity_type": "exercise",
            "action": "UPSERT",
            "payload": {
                "id": str(uuid4()),
                "name": "Too large",
                "description": "x" * 100_000,
                "primary_muscle_group": "Core",
                "equipment": "None",
                "tracking_type": "REPS",
                "notes": "",
                "base_revision": None,
            },
        },
        {
            "entity_type": "profile",
            "action": "UPSERT",
            "payload": {
                "display_name": "unsafe\u202ename",
                "unit_system": "METRIC",
                "onboarding_status": "NOT_STARTED",
            },
        },
        {
            "entity_type": "profile",
            "action": "UPSERT",
            "payload": {
                "display_name": "Guest",
                "unit_system": "INVALID",
                "onboarding_status": "NOT_STARTED",
            },
        },
    ],
)
async def test_malformed_sync_operations_return_422(app_client, operation) -> None:
    client, _ = app_client
    token, _ = await create_guest(client)
    operation["operation_id"] = str(uuid4())
    response = await client.post(
        "/api/v1/sync/push",
        headers={"Authorization": f"Bearer {token}"},
        json={"operations": [operation]},
    )
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"


async def _assert_cross_user_workout_reference_is_rejected(app_client) -> None:
    client, container = app_client
    alice_token, _ = await create_guest(client, key="sync-alice")
    bob_token, _ = await create_guest(client, key="sync-bob")
    exercise_id = str(uuid4())
    created = await client.post(
        "/api/v1/exercises",
        headers={"Authorization": f"Bearer {alice_token}"},
        json={"id": exercise_id, "name": "Alice private"},
    )
    assert created.status_code == 201
    response = await client.post(
        "/api/v1/sync/push",
        headers={"Authorization": f"Bearer {bob_token}"},
        json={
            "operations": [
                {
                    "operation_id": str(uuid4()),
                    "entity_type": "workout",
                    "action": "UPSERT",
                    "payload": {
                        "id": str(uuid4()),
                        "title": "Rejected",
                        "notes": "",
                        "exercise_ids": [exercise_id],
                    },
                }
            ]
        },
    )
    assert response.status_code == 422
    async with container.database.session_factory() as session:
        assert await session.scalar(select(func.count()).select_from(WorkoutRow)) == 0
        assert await session.scalar(select(func.count()).select_from(WorkoutExerciseRow)) == 0


async def test_cross_user_workout_reference_is_rejected_on_sqlite(app_client) -> None:
    await _assert_cross_user_workout_reference_is_rejected(app_client)


async def test_cross_user_workout_reference_is_rejected_on_postgresql(
    postgres_app_client,
) -> None:
    await _assert_cross_user_workout_reference_is_rejected(postgres_app_client)


async def test_operation_id_replays_and_rejects_changed_payload(app_client) -> None:
    client, _ = app_client
    token, profile = await create_guest(client)
    operation_id = str(uuid4())

    def request(display_name: str, batch_key: str):
        return client.post(
            "/api/v1/sync/push",
            headers={
                "Authorization": f"Bearer {token}",
                "Idempotency-Key": batch_key,
            },
            json={
                "operations": [
                    {
                        "operation_id": operation_id,
                        "entity_type": "profile",
                        "action": "UPSERT",
                        "payload": {
                            "display_name": display_name,
                            "unit_system": "METRIC",
                            "onboarding_status": "NOT_STARTED",
                        },
                    }
                ]
            },
        )

    first = await request("Stable", "batch-a")
    replay = await request("Stable", "batch-b")
    changed = await request("Changed", "batch-c")
    assert first.status_code == replay.status_code == 200
    assert first.json() == replay.json()
    assert changed.status_code == 409
    current = await client.get("/api/v1/profile", headers={"Authorization": f"Bearer {token}"})
    assert current.json()["user_id"] == profile["user_id"]
    assert current.json()["display_name"] == "Stable"


async def test_parallel_operation_id_replays_on_postgresql(postgres_app_client) -> None:
    client, _ = postgres_app_client
    token, _ = await create_guest(client)
    operation_id = str(uuid4())
    payload = {
        "operations": [
            {
                "operation_id": operation_id,
                "entity_type": "profile",
                "action": "UPSERT",
                "payload": {
                    "display_name": "Parallel",
                    "unit_system": "METRIC",
                    "onboarding_status": "NOT_STARTED",
                },
            }
        ]
    }
    responses = await asyncio.gather(
        *(
            client.post(
                "/api/v1/sync/push",
                headers={
                    "Authorization": f"Bearer {token}",
                    "Idempotency-Key": f"parallel-batch-{index}",
                },
                json=payload,
            )
            for index in range(20)
        )
    )
    assert all(response.status_code == 200 for response in responses)
    assert len({response.text for response in responses}) == 1


async def test_workout_sync_uses_lifecycle_commands(app_client) -> None:
    client, _ = app_client
    token, _ = await create_guest(client)
    workout_id = str(uuid4())
    auth = {"Authorization": f"Bearer {token}"}

    async def push(action: str, payload: dict[str, object]):
        return await client.post(
            "/api/v1/sync/push",
            headers=auth,
            json={
                "operations": [
                    {
                        "operation_id": str(uuid4()),
                        "entity_type": "workout",
                        "action": action,
                        "payload": payload,
                    }
                ]
            },
        )

    created = await push(
        "UPSERT",
        {"id": workout_id, "title": "Synced", "notes": "", "exercise_ids": []},
    )
    started = await push("START", {"id": workout_id})
    completed = await push("COMPLETE", {"id": workout_id})
    unsafe_update = await push(
        "UPSERT",
        {"id": workout_id, "title": "Rewrite", "notes": "", "exercise_ids": []},
    )
    assert created.status_code == started.status_code == completed.status_code == 200
    assert unsafe_update.status_code == 409
    workout = await client.get("/api/v1/workouts", headers=auth)
    persisted = workout.json()["items"][0]
    assert persisted["status"] == "COMPLETED"
    assert persisted["start_time"] is not None
    assert persisted["end_time"] is not None


async def _assert_action_is_bound_to_operation_id(app_client) -> None:
    client, _ = app_client
    token, _ = await create_guest(client)
    workout_id = str(uuid4())
    operation_id = str(uuid4())
    auth = {"Authorization": f"Bearer {token}"}

    async def push(operation: str, action: str, key: str):
        return await client.post(
            "/api/v1/sync/push",
            headers={**auth, "Idempotency-Key": key},
            json={
                "operations": [
                    {
                        "operation_id": operation,
                        "entity_type": "workout",
                        "action": action,
                        "payload": {"id": workout_id},
                    }
                ]
            },
        )

    created = await client.post(
        "/api/v1/workouts", headers=auth, json={"id": workout_id, "title": "Lifecycle"}
    )
    started = await push(operation_id, "START", "start-action")
    replay = await push(operation_id, "START", "start-replay")
    collision = await push(operation_id, "COMPLETE", "complete-collision")
    completed = await push(str(uuid4()), "COMPLETE", "complete-valid")
    assert created.status_code == 201
    assert started.status_code == replay.status_code == 200
    assert started.json() == replay.json()
    assert collision.status_code == 409
    assert completed.status_code == 200


async def test_sync_operation_hash_binds_action_on_sqlite(app_client) -> None:
    await _assert_action_is_bound_to_operation_id(app_client)


async def test_sync_operation_hash_binds_action_on_postgresql(postgres_app_client) -> None:
    await _assert_action_is_bound_to_operation_id(postgres_app_client)


def test_sync_operation_hash_binds_entity_and_contract_semantics() -> None:
    operation_id = uuid4()
    payload = WorkoutStartPayload(uuid4())
    workout = SyncCommand(operation_id, "workout", "START", payload)
    same = SyncCommand(operation_id, "workout", "START", payload)
    other_entity = SyncCommand(operation_id, "exercise", "START", payload)
    other_action = SyncCommand(operation_id, "workout", "COMPLETE", payload)
    assert canonical_request_hash(workout) == canonical_request_hash(same)
    assert canonical_request_hash(workout) != canonical_request_hash(other_entity)
    assert canonical_request_hash(workout) != canonical_request_hash(other_action)


async def _assert_expired_operation_can_be_reused(app_client, *, parallel: bool = False) -> None:
    client, container = app_client
    token, _ = await create_guest(client)
    operation_id = str(uuid4())
    auth = {"Authorization": f"Bearer {token}"}

    def payload(name: str) -> dict[str, object]:
        return {
            "operations": [
                {
                    "operation_id": operation_id,
                    "entity_type": "profile",
                    "action": "UPSERT",
                    "payload": {
                        "display_name": name,
                        "unit_system": "METRIC",
                        "onboarding_status": "NOT_STARTED",
                    },
                }
            ]
        }

    first = await client.post(
        "/api/v1/sync/push",
        headers={**auth, "Idempotency-Key": "expiry-first"},
        json=payload("Before expiry"),
    )
    assert first.status_code == 200
    async with container.database.session_factory() as session:
        await session.execute(
            update(ProcessedSyncOperationRow)
            .where(ProcessedSyncOperationRow.operation_id == UUID(operation_id))
            .values(expires_at=datetime.now(UTC) - timedelta(seconds=1))
        )
        await session.commit()

    if parallel:
        responses = await asyncio.gather(
            *(
                client.post(
                    "/api/v1/sync/push",
                    headers={**auth, "Idempotency-Key": f"expiry-reuse-{index}"},
                    json=payload("After expiry"),
                )
                for index in range(10)
            )
        )
        assert all(response.status_code == 200 for response in responses)
        assert len({response.text for response in responses}) == 1
    else:
        response = await client.post(
            "/api/v1/sync/push",
            headers={**auth, "Idempotency-Key": "expiry-reuse"},
            json=payload("After expiry"),
        )
        assert response.status_code == 200, response.text
    current = await client.get("/api/v1/profile", headers=auth)
    assert current.json()["display_name"] == "After expiry"


async def test_expired_operation_can_be_reused_on_sqlite(app_client) -> None:
    await _assert_expired_operation_can_be_reused(app_client)


async def test_expired_operation_parallel_reuse_on_postgresql(postgres_app_client) -> None:
    await _assert_expired_operation_can_be_reused(postgres_app_client, parallel=True)
