import pytest

from fitness_platform.domain.enums import CatalogStatus
from tests.catalog_factory import (
    catalog_exercise,
    persist_catalog_release,
)
from tests.conftest import create_guest


async def test_catalog_is_public_and_supports_combined_filters(app_client) -> None:
    client, container = app_client
    first = catalog_exercise("demo-squat", equipment="barbell")
    second = catalog_exercise("demo-row", muscle="back", equipment="dumbbells")
    await persist_catalog_release(container, [first, second])

    response = await client.get("/api/v1/catalog/exercises?muscle=legs&equipment=barbell")
    assert response.status_code == 200, response.text
    assert response.json()["catalog_version"] == "test-release-v1"
    assert response.json()["content_hash"].startswith("sha256:")
    assert [item["id"] for item in response.json()["items"]] == [str(first.id)]
    assert (await client.get("/api/v1/catalog/exercises?muscle=unknown")).json()["items"] == []
    assert (await client.get("/api/v1/catalog/exercises?equipment=unknown")).json()["items"] == []
    assert (await client.get("/api/v1/catalog/muscles")).json() == [
        {"slug": "back", "name": "Back"},
        {"slug": "legs", "name": "Legs"},
    ]
    assert (await client.get("/api/v1/catalog/equipment")).json() == [
        {"slug": "barbell", "name": "Barbell"},
        {"slug": "dumbbells", "name": "Dumbbells"},
    ]
    assert (await client.get(f"/api/v1/catalog/exercises/{first.id}")).status_code == 200


async def test_private_exercises_never_appear_in_catalog(app_client) -> None:
    client, container = app_client
    await persist_catalog_release(container, [])
    token, _ = await create_guest(client)
    response = await client.post(
        "/api/v1/exercises",
        headers={"Authorization": f"Bearer {token}", "Idempotency-Key": "private"},
        json={
            "name": "Private only",
            "primary_muscle_group": "Legs",
            "equipment": "None",
            "tracking_type": "REPS",
        },
    )
    assert response.status_code == 201, response.text
    catalog = await client.get("/api/v1/catalog/exercises")
    assert all(item["name"] != "Private only" for item in catalog.json()["items"])


async def test_public_api_hides_draft_deprecated_and_unreviewed_entries(app_client) -> None:
    client, container = app_client
    draft = catalog_exercise("draft", status=CatalogStatus.DRAFT, reviewed=True)
    deprecated = catalog_exercise("deprecated", status=CatalogStatus.DEPRECATED)
    hidden = catalog_exercise("unreviewed", status=CatalogStatus.DRAFT, reviewed=False)
    await persist_catalog_release(container, [draft, deprecated, hidden])
    assert (await client.get("/api/v1/catalog/exercises")).json()["items"] == []
    assert (await client.get(f"/api/v1/catalog/exercises/{hidden.id}")).status_code == 404


async def test_catalog_search_pagination_snapshot_and_etag(app_client) -> None:
    client, container = app_client
    zeta = catalog_exercise("zeta-row", muscle="back")
    alpha = catalog_exercise("alpha-squat", muscle="legs")
    alpha.name = "Alpha Squat"
    await persist_catalog_release(container, [zeta, alpha])

    search = await client.get("/api/v1/catalog/exercises?q=alpha&limit=1&offset=0")
    assert search.status_code == 200
    assert [item["name"] for item in search.json()["items"]] == ["Alpha Squat"]
    page = await client.get("/api/v1/catalog/exercises?limit=1&offset=1")
    assert len(page.json()["items"]) == 1
    assert page.json()["page"]["total"] == 2

    snapshot = await client.get("/api/v1/catalog/snapshot")
    assert snapshot.status_code == 200
    assert snapshot.json()["schema_version"] == "1"
    assert snapshot.json()["content_hash"].startswith("sha256:")
    assert snapshot.json()["total"] == 2
    assert [item["name"] for item in snapshot.json()["exercises"]] == sorted(
        item["name"] for item in snapshot.json()["exercises"]
    )
    unchanged = await client.get(
        "/api/v1/catalog/snapshot", headers={"If-None-Match": snapshot.headers["ETag"]}
    )
    assert unchanged.status_code == 304


async def test_catalog_search_treats_sql_wildcards_as_literals(app_client) -> None:
    client, container = app_client
    percent = catalog_exercise("percent")
    percent.name = "100% Press"
    underscore = catalog_exercise("underscore")
    underscore.name = "Under_score Row"
    ordinary = catalog_exercise("ordinary")
    ordinary.name = "Ordinary Row"
    await persist_catalog_release(container, [percent, underscore, ordinary])

    assert [
        item["name"]
        for item in (await client.get("/api/v1/catalog/exercises?q=%25")).json()["items"]
    ] == ["100% Press"]
    assert [
        item["name"] for item in (await client.get("/api/v1/catalog/exercises?q=_")).json()["items"]
    ] == ["Under_score Row"]


@pytest.mark.parametrize("exercise_count", [0, 3, 100, 101, 501])
async def test_snapshot_never_truncates_a_published_release(
    app_client, exercise_count: int
) -> None:
    client, container = app_client
    exercises = [catalog_exercise(f"exercise-{index:04d}") for index in range(exercise_count)]
    await persist_catalog_release(container, exercises)

    snapshot = await client.get("/api/v1/catalog/snapshot")

    assert snapshot.status_code == 200
    assert snapshot.json()["total"] == exercise_count
    assert len(snapshot.json()["exercises"]) == exercise_count
