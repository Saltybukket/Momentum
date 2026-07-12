import asyncio

from httpx import AsyncClient
from sqlalchemy import select

from fitness_platform.container import AppContainer
from fitness_platform.infrastructure.orm import GuestSessionRow, IdempotencyRecordRow
from tests.conftest import create_guest


async def test_guest_profile_is_created_and_read(app_client: tuple[AsyncClient, object]) -> None:
    client, _ = app_client
    token, profile = await create_guest(client)

    response = await client.get("/api/v1/profile", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 200
    assert response.json()["user_id"] == profile["user_id"]
    assert response.json()["display_name"] == "Test Guest"


async def test_guest_recovery_rotates_token_without_idempotency_credential_storage(
    app_client: tuple[AsyncClient, AppContainer],
) -> None:
    client, container = app_client
    payload = {
        "display_name": "Guest",
        "installation_id": "1c9a05e8-b669-45a6-887e-33b8ea00899a",
        "recovery_secret": "correct-recovery-secret-000000000000",
    }
    first = await client.post("/api/v1/guest-sessions", json=payload)
    second = await client.post("/api/v1/guest-sessions", json=payload)

    assert first.status_code == 201
    assert second.status_code == 200
    assert first.json()["guest_token"] != second.json()["guest_token"]
    assert first.json()["profile"]["user_id"] == second.json()["profile"]["user_id"]
    async with container.database.session_factory() as session:
        guest = (await session.execute(select(GuestSessionRow))).scalar_one()
        records = (await session.execute(select(IdempotencyRecordRow))).scalars().all()
    assert first.json()["guest_token"] not in guest.token_hash
    assert second.json()["guest_token"] not in guest.token_hash
    assert records == []


async def test_guest_recovery_rejects_wrong_proof(
    app_client: tuple[AsyncClient, object],
) -> None:
    client, _ = app_client
    payload = {
        "display_name": "Guest",
        "installation_id": "1c9a05e8-b669-45a6-887e-33b8ea00899b",
        "recovery_secret": "correct-recovery-secret-000000000000",
    }
    first = await client.post("/api/v1/guest-sessions", json=payload)
    second = await client.post(
        "/api/v1/guest-sessions",
        json={**payload, "recovery_secret": "wrong-recovery-secret-00000000000000"},
    )

    assert first.status_code == 201
    assert second.status_code == 401
    assert second.json()["error"]["code"] == "UNAUTHORIZED"


async def test_guest_openapi_documents_create_recovery_and_concurrency(
    app_client: tuple[AsyncClient, object],
) -> None:
    client, _ = app_client
    responses = (await client.get("/openapi.json")).json()["paths"]["/api/v1/guest-sessions"][
        "post"
    ]["responses"]
    assert {"200", "201", "409", "422"} <= responses.keys()


async def _assert_parallel_create_is_serialized(
    client: AsyncClient, container: AppContainer
) -> None:
    payload = {
        "display_name": "Concurrent Guest",
        "installation_id": "1c9a05e8-b669-45a6-887e-33b8ea00899c",
        "recovery_secret": "concurrent-recovery-secret-000000000000",
    }

    responses = await asyncio.gather(
        *(client.post("/api/v1/guest-sessions", json=payload) for _ in range(20))
    )
    successful = [response for response in responses if response.status_code in {200, 201}]

    assert len(successful) == 1
    assert {response.status_code for response in responses} <= {201, 409}
    assert all(response.status_code != 500 for response in responses)
    token = successful[0].json()["guest_token"]
    profile = await client.get("/api/v1/profile", headers={"Authorization": f"Bearer {token}"})
    assert profile.status_code == 200
    async with container.database.session_factory() as session:
        sessions = (await session.execute(select(GuestSessionRow))).scalars().all()
    assert len(sessions) == 1


async def _assert_parallel_recovery_is_serialized(
    client: AsyncClient, container: AppContainer
) -> None:
    payload = {
        "display_name": "Recovery Guest",
        "installation_id": "1c9a05e8-b669-45a6-887e-33b8ea00899d",
        "recovery_secret": "parallel-recovery-secret-00000000000000",
    }
    created = await client.post("/api/v1/guest-sessions", json=payload)
    old_token = created.json()["guest_token"]
    responses = await asyncio.gather(
        *(client.post("/api/v1/guest-sessions", json=payload) for _ in range(20))
    )
    successful = [response for response in responses if response.status_code == 200]

    assert len(successful) == 1
    assert {response.status_code for response in responses} <= {200, 409}
    new_token = successful[0].json()["guest_token"]
    old_profile = await client.get(
        "/api/v1/profile", headers={"Authorization": f"Bearer {old_token}"}
    )
    new_profile = await client.get(
        "/api/v1/profile", headers={"Authorization": f"Bearer {new_token}"}
    )
    assert old_profile.status_code == 401
    assert new_profile.status_code == 200
    async with container.database.session_factory() as session:
        sessions = (await session.execute(select(GuestSessionRow))).scalars().all()
    assert len(sessions) == 1


async def test_parallel_guest_create_and_recovery_are_serialized_on_sqlite(
    app_client: tuple[AsyncClient, AppContainer],
) -> None:
    await _assert_parallel_create_is_serialized(*app_client)


async def test_parallel_guest_recovery_is_serialized_on_sqlite(
    app_client: tuple[AsyncClient, AppContainer],
) -> None:
    await _assert_parallel_recovery_is_serialized(*app_client)


async def test_parallel_guest_create_is_serialized_on_postgresql(
    postgres_app_client: tuple[AsyncClient, AppContainer],
) -> None:
    await _assert_parallel_create_is_serialized(*postgres_app_client)


async def test_parallel_guest_recovery_is_serialized_on_postgresql(
    postgres_app_client: tuple[AsyncClient, AppContainer],
) -> None:
    await _assert_parallel_recovery_is_serialized(*postgres_app_client)
