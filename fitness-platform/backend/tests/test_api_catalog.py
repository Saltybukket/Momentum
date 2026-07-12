from datetime import UTC, datetime
from uuid import uuid4

import pytest
from sqlalchemy import select

from fitness_platform.domain.enums import CatalogStatus, MuscleRole, TrackingType
from fitness_platform.infrastructure.orm import CatalogExerciseRow
from tests.catalog_factory import (
    catalog_exercise,
    persist_catalog_exercise,
    persist_catalog_release,
)
from tests.conftest import create_guest


async def test_catalog_is_public_and_supports_combined_filters(app_client) -> None:
    client, container = app_client
    first = await persist_catalog_exercise(
        container, catalog_exercise("demo-squat", equipment="barbell")
    )
    await persist_catalog_exercise(
        container, catalog_exercise("demo-row", muscle="back", equipment="dumbbells")
    )

    response = await client.get("/api/v1/catalog/exercises?muscle=legs&equipment=barbell")
    assert response.status_code == 200, response.text
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


async def test_catalog_upsert_is_idempotent_and_replaces_relations(app_client) -> None:
    _client, container = app_client
    original = await persist_catalog_exercise(
        container, catalog_exercise("demo-squat", equipment="none")
    )
    update = catalog_exercise("demo-squat", muscle="back", equipment="barbell")
    update.name = "Updated squat"
    updated = await persist_catalog_exercise(container, update)

    assert updated.id == original.id
    assert updated.name == "Updated squat"
    assert updated.muscles == [("back", MuscleRole.PRIMARY)]
    assert updated.equipment == ["barbell"]
    async with container.database.session_factory() as session:
        assert len((await session.execute(select(CatalogExerciseRow))).scalars().all()) == 1


async def test_private_exercises_never_appear_in_catalog(app_client) -> None:
    client, _container = app_client
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
    await persist_catalog_exercise(
        container, catalog_exercise("draft", status=CatalogStatus.DRAFT, reviewed=True)
    )
    await persist_catalog_exercise(
        container, catalog_exercise("deprecated", status=CatalogStatus.DEPRECATED)
    )
    hidden = await persist_catalog_exercise(
        container, catalog_exercise("unreviewed", status=CatalogStatus.DRAFT, reviewed=False)
    )
    assert (await client.get("/api/v1/catalog/exercises")).json()["items"] == []
    assert (await client.get(f"/api/v1/catalog/exercises/{hidden.id}")).status_code == 404


async def test_catalog_search_pagination_snapshot_and_etag(app_client) -> None:
    client, container = app_client
    await persist_catalog_exercise(container, catalog_exercise("zeta-row", muscle="back"))
    alpha = catalog_exercise("alpha-squat", muscle="legs")
    alpha.name = "Alpha Squat"
    await persist_catalog_exercise(container, alpha)

    search = await client.get("/api/v1/catalog/exercises?q=alpha&limit=1&offset=0")
    assert search.status_code == 200
    assert [item["name"] for item in search.json()["items"]] == ["Alpha Squat"]
    page = await client.get("/api/v1/catalog/exercises?limit=1&offset=1")
    assert len(page.json()["items"]) == 1
    assert page.json()["page"]["total"] == 2

    await persist_catalog_release(container, 2)
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


@pytest.mark.parametrize("exercise_count", [0, 3, 100, 101, 488])
async def test_snapshot_never_truncates_a_published_release(
    app_client, exercise_count: int
) -> None:
    client, container = app_client
    now = datetime.now(UTC)
    async with container.database.session_factory() as session:
        session.add_all(
            CatalogExerciseRow(
                id=uuid4(),
                external_id=f"exercise-{index:04d}",
                source="scale-test",
                provenance="Self-authored scale fixture.",
                license_name="CC0-1.0",
                license_url="https://creativecommons.org/publicdomain/zero/1.0/",
                version="1",
                status=CatalogStatus.PUBLISHED,
                reviewed=True,
                name=f"Exercise {index:04d}",
                description="Scale fixture.",
                tracking_type=TrackingType.REPS,
                created_at=now,
                updated_at=now,
            )
            for index in range(exercise_count)
        )
        await session.commit()
    await persist_catalog_release(container, exercise_count)

    snapshot = await client.get("/api/v1/catalog/snapshot")

    assert snapshot.status_code == 200
    assert snapshot.json()["total"] == exercise_count
    assert len(snapshot.json()["exercises"]) == exercise_count
