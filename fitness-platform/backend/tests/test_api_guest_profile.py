from httpx import AsyncClient

from tests.conftest import create_guest


async def test_guest_profile_is_created_and_read(app_client: tuple[AsyncClient, object]) -> None:
    client, _ = app_client
    token, profile = await create_guest(client)

    response = await client.get("/api/v1/profile", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 200
    assert response.json()["user_id"] == profile["user_id"]
    assert response.json()["display_name"] == "Test Guest"


async def test_duplicate_guest_request_replays_same_result(
    app_client: tuple[AsyncClient, object],
) -> None:
    client, _ = app_client
    headers = {"Idempotency-Key": "same-guest-key"}
    first = await client.post(
        "/api/v1/guest-sessions", headers=headers, json={"display_name": "Guest"}
    )
    second = await client.post(
        "/api/v1/guest-sessions", headers=headers, json={"display_name": "Guest"}
    )

    assert first.status_code == second.status_code == 201
    assert first.json() == second.json()
    assert second.headers["Idempotency-Replayed"] == "true"


async def test_idempotency_key_rejects_changed_payload(
    app_client: tuple[AsyncClient, object],
) -> None:
    client, _ = app_client
    headers = {"Idempotency-Key": "reused-key"}
    first = await client.post("/api/v1/guest-sessions", headers=headers, json={"display_name": "A"})
    second = await client.post(
        "/api/v1/guest-sessions", headers=headers, json={"display_name": "B"}
    )

    assert first.status_code == 201
    assert second.status_code == 409
    assert second.json()["error"]["code"] == "CONFLICT"
