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
