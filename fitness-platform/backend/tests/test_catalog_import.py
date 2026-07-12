import json
from copy import deepcopy
from pathlib import Path
from uuid import NAMESPACE_URL, uuid5

import pytest
from sqlalchemy import select

from fitness_platform.catalog_import import (
    CatalogImportError,
    canonical_release_hash,
    import_catalog,
)
from fitness_platform.infrastructure.orm import CatalogExerciseRow, EquipmentRow


def demo_document() -> dict[str, object]:
    return {
        "schema_version": "1",
        "catalog_version": "test-v1",
        "published_at": "2026-07-12T00:00:00Z",
        "batch_id": "test-v1",
        "muscles": [{"slug": "legs", "name": "Legs"}],
        "equipment": [{"slug": "none", "name": "No equipment"}],
        "exercises": [
            {
                "id": str(uuid5(NAMESPACE_URL, "momentum-catalog:test:squat")),
                "source": "test",
                "external_id": "squat",
                "provenance": "Self-authored test fixture.",
                "license_name": "CC0-1.0",
                "license_url": "https://creativecommons.org/publicdomain/zero/1.0/",
                "version": "1",
                "status": "PUBLISHED",
                "reviewed": True,
                "name": "Squat",
                "description": "Self-authored test fixture.",
                "tracking_type": "REPS",
                "muscles": [{"slug": "legs", "role": "PRIMARY"}],
                "equipment": ["none"],
            }
        ],
    }


def write_document(tmp_path: Path, document: dict[str, object]) -> Path:
    path = tmp_path / "catalog.json"
    document["content_hash"] = canonical_release_hash(document)
    path.write_text(json.dumps(document), encoding="utf-8")
    return path


async def test_import_is_idempotent_and_reports_changed_updates(app_client, tmp_path) -> None:
    _client, container = app_client
    document = demo_document()
    path = write_document(tmp_path, document)
    first = await import_catalog(container, path)
    second = await import_catalog(container, path)
    assert (first["created"], second["unchanged"]) == (1, 1)
    document["exercises"][0]["name"] = "Updated squat"  # type: ignore[index]
    changed = await import_catalog(container, write_document(tmp_path, document))
    assert changed["updated"] == 1
    assert changed["licenses"] == ["CC0-1.0"]
    document["equipment"][0]["name"] = "Nothing required"  # type: ignore[index]
    await import_catalog(container, write_document(tmp_path, document))
    async with container.database.session_factory() as session:
        equipment = (await session.execute(select(EquipmentRow))).scalar_one()
    assert equipment.name == "Nothing required"


@pytest.mark.parametrize(
    ("mutation", "message"),
    [
        (lambda doc: doc["exercises"].append(deepcopy(doc["exercises"][0])), "Duplicate"),
        (lambda doc: doc["exercises"][0].update(source=""), "source"),
        (lambda doc: doc["exercises"][0].update(license_name=""), "license"),
        (
            lambda doc: doc["exercises"][0]["muscles"].append(
                {"slug": "unknown", "role": "SECONDARY"}
            ),
            "Unknown muscle",
        ),
        (lambda doc: doc["exercises"][0].update(equipment=["unknown"]), "Unknown equipment"),
        (lambda doc: doc["exercises"][0].update(reviewed=False), "before review"),
    ],
)
async def test_import_rejects_invalid_batches_atomically(
    app_client, tmp_path, mutation, message
) -> None:
    _client, container = app_client
    document = demo_document()
    mutation(document)
    with pytest.raises(CatalogImportError, match=message):
        await import_catalog(container, write_document(tmp_path, document))
    async with container.database.session_factory() as session:
        assert (await session.execute(select(CatalogExerciseRow))).scalars().all() == []


async def test_draft_or_unreviewed_entries_are_imported_but_not_public(
    app_client, tmp_path
) -> None:
    client, container = app_client
    document = demo_document()
    document["exercises"][0].update(status="DRAFT", reviewed=False)  # type: ignore[index]
    result = await import_catalog(container, write_document(tmp_path, document))
    assert result["created"] == 1
    assert (await client.get("/api/v1/catalog/exercises")).json()["items"] == []
