import pytest

from tests.conftest import create_guest


@pytest.mark.parametrize("unsafe", ["bad\x00name", "bad\u202ename", "bad\nname", "   "])
async def test_single_line_fields_reject_unsafe_text(app_client, unsafe: str) -> None:
    client, _ = app_client
    token, _ = await create_guest(client)
    auth = {"Authorization": f"Bearer {token}"}

    profile = await client.put(
        "/api/v1/profile",
        headers=auth,
        json={
            "display_name": unsafe,
            "unit_system": "METRIC",
            "onboarding_status": "NOT_STARTED",
        },
    )
    exercise = await client.post("/api/v1/exercises", headers=auth, json={"name": unsafe})
    workout = await client.post("/api/v1/workouts", headers=auth, json={"title": unsafe})
    assert profile.status_code == exercise.status_code == workout.status_code == 422


async def _assert_multiline_policy_matches_crud_and_sync(app_client) -> None:
    client, _ = app_client
    token, _ = await create_guest(client)
    auth = {"Authorization": f"Bearer {token}"}
    allowed = await client.post(
        "/api/v1/exercises",
        headers=auth,
        json={
            "name": "Übung 東京",
            "description": "First line\nSecond line\tvalue",
            "notes": "Allowed\nnotes",
        },
    )
    rejected = await client.post(
        "/api/v1/exercises",
        headers=auth,
        json={"name": "Safe", "notes": "unsafe\u202evalue"},
    )
    assert allowed.status_code == 201, allowed.text
    assert allowed.json()["description"] == "First line\nSecond line\tvalue"
    assert rejected.status_code == 422


async def test_multiline_policy_matches_on_sqlite(app_client) -> None:
    await _assert_multiline_policy_matches_crud_and_sync(app_client)


async def test_multiline_policy_matches_on_postgresql(postgres_app_client) -> None:
    await _assert_multiline_policy_matches_crud_and_sync(postgres_app_client)
