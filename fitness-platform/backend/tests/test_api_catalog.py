from sqlalchemy import select

from fitness_platform.domain.enums import CatalogStatus, MuscleRole
from fitness_platform.infrastructure.orm import CatalogExerciseRow
from tests.catalog_factory import catalog_exercise, persist_catalog_exercise
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
    assert [item["id"] for item in response.json()] == [str(first.id)]
    assert (await client.get("/api/v1/catalog/exercises?muscle=unknown")).json() == []
    assert (await client.get("/api/v1/catalog/exercises?equipment=unknown")).json() == []
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
    assert all(item["name"] != "Private only" for item in catalog.json())


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
    assert (await client.get("/api/v1/catalog/exercises")).json() == []
    assert (await client.get(f"/api/v1/catalog/exercises/{hidden.id}")).status_code == 404
