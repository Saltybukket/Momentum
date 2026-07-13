import asyncio
import json
from copy import deepcopy
from datetime import UTC, datetime, timedelta
from pathlib import Path
from uuid import NAMESPACE_URL, uuid5

import pytest
from sqlalchemy import func, select

from fitness_platform.catalog_import import (
    CatalogImportError,
    CatalogImportErrorCode,
    activate_catalog_release,
    canonical_release_hash,
    import_catalog,
)
from fitness_platform.domain.enums import CatalogReleaseStatus
from fitness_platform.infrastructure.orm import (
    CatalogActivationRow,
    CatalogReleaseExerciseRow,
    CatalogReleaseRow,
)

DATA_DIR = Path(__file__).resolve().parents[2] / "data" / "exercises"


def demo_document(
    *,
    catalog_version: str = "test-v1",
    published_at: datetime | None = None,
) -> dict[str, object]:
    return {
        "schema_version": "1",
        "catalog_version": catalog_version,
        "published_at": (published_at or datetime(2026, 7, 12, tzinfo=UTC))
        .isoformat()
        .replace("+00:00", "Z"),
        "batch_id": catalog_version,
        "muscles": [{"slug": "legs", "name": "Legs"}],
        "equipment": [{"slug": "none", "name": "No equipment"}],
        "exercises": [exercise("squat")],
    }


def exercise(external_id: str) -> dict[str, object]:
    return {
        "id": str(uuid5(NAMESPACE_URL, f"momentum-catalog:test:{external_id}")),
        "source": "test",
        "external_id": external_id,
        "provenance": "Self-authored test fixture.",
        "license_name": "CC0-1.0",
        "license_url": "https://creativecommons.org/publicdomain/zero/1.0/",
        "version": "1",
        "status": "PUBLISHED",
        "reviewed": True,
        "name": external_id.replace("-", " ").title(),
        "description": "Self-authored test fixture.",
        "tracking_type": "REPS",
        "muscles": [{"slug": "legs", "role": "PRIMARY"}],
        "equipment": ["none"],
    }


def write_document(tmp_path: Path, document: dict[str, object], name: str = "catalog.json") -> Path:
    document["content_hash"] = canonical_release_hash(document)
    path = tmp_path / name
    path.write_text(json.dumps(document), encoding="utf-8")
    return path


def write_untrusted_document(
    tmp_path: Path, document: dict[str, object], name: str = "untrusted-catalog.json"
) -> Path:
    document["content_hash"] = "sha256:" + "0" * 64
    path = tmp_path / name
    path.write_text(json.dumps(document), encoding="utf-8")
    return path


async def test_identical_reimport_is_a_true_noop(app_client, tmp_path) -> None:
    _client, container = app_client
    document = demo_document()
    path = write_document(tmp_path, document)
    first = await import_catalog(container, path)
    async with container.database.session_factory() as session:
        initial = (await session.execute(select(CatalogReleaseExerciseRow))).scalar_one()
        initial_updated_at = initial.updated_at

    second = await import_catalog(container, path)

    assert first["created"] == 1
    assert first["activated"] is True
    assert second["unchanged"] == 1
    assert second["activated"] is False
    assert second["release_status"] == "UNCHANGED"
    async with container.database.session_factory() as session:
        rows = (await session.execute(select(CatalogReleaseExerciseRow))).scalars().all()
        assert len(rows) == 1
        assert rows[0].updated_at == initial_updated_at


async def test_snapshot_hash_is_semantic_and_matches_exact_api_shape(app_client, tmp_path) -> None:
    client, container = app_client
    document = demo_document(catalog_version="canonical-v1")
    document["muscles"] = [
        {"slug": "upper-back", "name": "Upper back"},
        {"slug": "legs", "name": "Legs"},
    ]
    document["equipment"] = [
        {"slug": "rack", "name": "Rack"},
        {"slug": "none", "name": "No equipment"},
    ]
    second = exercise("row")
    second["muscles"] = [
        {"slug": "upper-back", "role": "SECONDARY"},
        {"slug": "legs", "role": "PRIMARY"},
    ]
    second["equipment"] = ["rack", "none"]
    document["exercises"] = [second, document["exercises"][0]]  # type: ignore[index]
    shuffled = deepcopy(document)
    shuffled["muscles"] = list(reversed(shuffled["muscles"]))  # type: ignore[arg-type]
    shuffled["equipment"] = list(reversed(shuffled["equipment"]))  # type: ignore[arg-type]
    shuffled["exercises"] = list(reversed(shuffled["exercises"]))  # type: ignore[arg-type]
    for item in shuffled["exercises"]:  # type: ignore[union-attr]
        item["muscles"] = list(reversed(item["muscles"]))
        item["equipment"] = list(reversed(item["equipment"]))

    assert canonical_release_hash(document) == canonical_release_hash(shuffled)
    await import_catalog(container, write_document(tmp_path, shuffled))

    snapshot = await client.get("/api/v1/catalog/snapshot")
    page = await client.get("/api/v1/catalog/exercises")
    assert snapshot.status_code == 200
    assert canonical_release_hash(snapshot.json()) == snapshot.json()["content_hash"]
    assert (
        snapshot.headers["ETag"] == f'"{snapshot.json()["content_hash"].removeprefix("sha256:")}"'
    )
    assert page.json()["catalog_version"] == snapshot.json()["catalog_version"]
    assert page.json()["content_hash"] == snapshot.json()["content_hash"]
    assert [item["slug"] for item in snapshot.json()["muscles"]] == ["legs", "upper-back"]
    assert [item["slug"] for item in snapshot.json()["equipment"]] == ["none", "rack"]
    assert [
        (item["source"], item["external_id"], item["id"]) for item in snapshot.json()["exercises"]
    ] == sorted(
        (item["source"], item["external_id"], item["id"]) for item in snapshot.json()["exercises"]
    )


async def test_demo_api_snapshot_matches_cross_platform_contract(app_client) -> None:
    client, container = app_client
    await import_catalog(container, DATA_DIR / "catalog-demo.json")

    response = await client.get("/api/v1/catalog/snapshot")
    expected = json.loads((DATA_DIR / "catalog-demo-api-snapshot.json").read_text())

    assert response.status_code == 200
    assert response.json() == expected
    assert canonical_release_hash(response.json()) == response.json()["content_hash"]


def test_semantic_hash_changes_when_content_changes() -> None:
    document = demo_document()
    changed = deepcopy(document)
    changed["exercises"][0]["description"] = "Actually different"  # type: ignore[index]
    assert canonical_release_hash(document) != canonical_release_hash(changed)


async def test_same_version_with_different_hash_is_rejected_and_active_unchanged(
    app_client, tmp_path
) -> None:
    client, container = app_client
    original = demo_document()
    await import_catalog(container, write_document(tmp_path, original))
    changed = deepcopy(original)
    changed["exercises"][0]["name"] = "Changed"  # type: ignore[index]

    with pytest.raises(CatalogImportError, match="immutable"):
        await import_catalog(container, write_document(tmp_path, changed))

    snapshot = (await client.get("/api/v1/catalog/snapshot")).json()
    assert snapshot["catalog_version"] == "test-v1"
    assert snapshot["exercises"][0]["name"] == "Squat"


@pytest.mark.parametrize(
    ("mutation", "message"),
    [
        (lambda doc: doc["exercises"].append(deepcopy(doc["exercises"][0])), "Duplicate"),
        (lambda doc: doc["exercises"][0].update(source=""), "source"),
        (lambda doc: doc["exercises"][0].update(license_name=""), "license"),
        (
            lambda doc: doc["exercises"][0]["muscles"].append(
                {"slug": "legs", "role": "SECONDARY"}
            ),
            "duplicate muscle",
        ),
        (
            lambda doc: doc["exercises"][0].update(equipment=["none", "none"]),
            "non-unique|duplicate equipment",
        ),
        (
            lambda doc: doc["exercises"][0]["muscles"].append(
                {"slug": "unknown", "role": "SECONDARY"}
            ),
            "Unknown muscle",
        ),
        (lambda doc: doc["exercises"][0].update(equipment=["unknown"]), "Unknown equipment"),
        (lambda doc: doc["exercises"][0].update(reviewed=False), "review"),
        (lambda doc: doc["exercises"][0].update(status="DRAFT"), "not approved"),
        (
            lambda doc: doc["exercises"][0].update(license_url="javascript:alert(1)"),
            "https",
        ),
    ],
)
async def test_invalid_release_is_rejected_before_any_write(
    app_client, tmp_path, mutation, message
) -> None:
    _client, container = app_client
    document = demo_document()
    mutation(document)

    with pytest.raises(CatalogImportError, match=message):
        await import_catalog(container, write_document(tmp_path, document))

    async with container.database.session_factory() as session:
        assert await session.scalar(select(func.count()).select_from(CatalogReleaseRow)) == 0
        assert (
            await session.scalar(select(func.count()).select_from(CatalogReleaseExerciseRow)) == 0
        )


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("catalog_version", "bad\nversion"),
        ("batch_id", "bad\u202ebatch"),
    ],
)
async def test_unsafe_release_identity_is_rejected(app_client, tmp_path, field, value) -> None:
    _client, container = app_client
    document = demo_document()
    document[field] = value
    with pytest.raises(CatalogImportError, match=r"control|single-line"):
        await import_catalog(container, write_untrusted_document(tmp_path, document))


@pytest.mark.parametrize(
    "value",
    [
        "bad\x00name",
        "bad\u202ename",
        "bad\nname",
    ],
)
async def test_unsafe_exercise_text_is_rejected_on_sqlite(app_client, tmp_path, value) -> None:
    _client, container = app_client
    document = demo_document()
    document["exercises"][0]["name"] = value  # type: ignore[index]
    with pytest.raises(CatalogImportError, match=r"control|single-line"):
        await import_catalog(container, write_untrusted_document(tmp_path, document))


@pytest.mark.parametrize(
    ("url", "check_schema_formats"),
    [
        ("https:foo", True),
        ("https:/foo", True),
        ("https://", True),
        ("https://user:pass@example.com/", True),
        ("https://example.com:invalid/", True),
        ("https://example.com:invalid/", False),
        ("https://exa mple.com/", True),
    ],
)
async def test_non_absolute_or_credentialed_https_url_is_rejected(
    app_client, tmp_path, url, check_schema_formats
) -> None:
    _client, container = app_client
    document = demo_document()
    document["exercises"][0]["license_url"] = url  # type: ignore[index]
    with pytest.raises(CatalogImportError) as captured:
        await import_catalog(
            container,
            write_untrusted_document(tmp_path, document),
            check_schema_formats=check_schema_formats,
        )

    assert captured.value.code in {
        CatalogImportErrorCode.INVALID_LICENSE_URL,
        CatalogImportErrorCode.SCHEMA_VALIDATION,
    }
    assert captured.value.field_path is not None
    assert captured.value.field_path.endswith("license_url")
    async with container.database.session_factory() as session:
        assert await session.scalar(select(func.count()).select_from(CatalogReleaseRow)) == 0
        assert await session.scalar(select(func.count()).select_from(CatalogActivationRow)) == 0


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("name", "bad\x00name"),
        ("name", "bad\u202ename"),
        ("license_url", "https:foo"),
        ("license_url", "https://user:pass@example.com/"),
    ],
)
async def test_unsafe_catalog_input_is_rejected_on_postgresql(
    postgres_app_client, tmp_path, field, value
) -> None:
    _client, container = postgres_app_client
    document = demo_document()
    document["exercises"][0][field] = value  # type: ignore[index]
    with pytest.raises(CatalogImportError):
        await import_catalog(container, write_untrusted_document(tmp_path, document))


async def test_full_release_removes_missing_exercises_but_keeps_history(
    app_client, tmp_path
) -> None:
    client, container = app_client
    first = demo_document(catalog_version="v1")
    first["exercises"].append(exercise("row"))  # type: ignore[union-attr]
    await import_catalog(container, write_document(tmp_path, first, "v1.json"))
    second = demo_document(catalog_version="v2", published_at=datetime(2026, 7, 13, tzinfo=UTC))
    await import_catalog(container, write_document(tmp_path, second, "v2.json"))

    snapshot = (await client.get("/api/v1/catalog/snapshot")).json()
    assert snapshot["catalog_version"] == "v2"
    assert [item["external_id"] for item in snapshot["exercises"]] == ["squat"]
    async with container.database.session_factory() as session:
        assert (
            await session.scalar(select(func.count()).select_from(CatalogReleaseExerciseRow)) == 3
        )


async def test_older_import_is_staged_without_rolling_back_active_release(
    app_client, tmp_path
) -> None:
    client, container = app_client
    newer = demo_document(catalog_version="v2", published_at=datetime(2026, 7, 13, tzinfo=UTC))
    older = demo_document(catalog_version="v1", published_at=datetime(2026, 7, 12, tzinfo=UTC))
    await import_catalog(container, write_document(tmp_path, newer, "v2.json"))

    report = await import_catalog(container, write_document(tmp_path, older, "v1.json"))

    assert report["activated"] is False
    assert report["release_status"] == CatalogReleaseStatus.RETIRED.value
    assert (await client.get("/api/v1/catalog/snapshot")).json()["catalog_version"] == "v2"


async def test_explicit_rollback_atomically_reactivates_historical_release(
    app_client, tmp_path
) -> None:
    client, container = app_client
    first = demo_document(catalog_version="v1")
    first["exercises"].append(exercise("row"))  # type: ignore[union-attr]
    second = demo_document(catalog_version="v2", published_at=datetime(2026, 7, 13, tzinfo=UTC))
    await import_catalog(container, write_document(tmp_path, first, "v1.json"))
    await import_catalog(container, write_document(tmp_path, second, "v2.json"))

    result = await activate_catalog_release(container, "v1")

    assert result["activated"] is True
    snapshot = (await client.get("/api/v1/catalog/snapshot")).json()
    assert snapshot["catalog_version"] == "v1"
    assert snapshot["total"] == 2


async def test_duplicate_batch_identity_and_unknown_activation_leave_active_unchanged(
    app_client, tmp_path
) -> None:
    client, container = app_client
    first = demo_document(catalog_version="v1")
    await import_catalog(container, write_document(tmp_path, first, "v1.json"))
    duplicate_batch = demo_document(
        catalog_version="v2", published_at=datetime(2026, 7, 13, tzinfo=UTC)
    )
    duplicate_batch["batch_id"] = "v1"

    with pytest.raises(CatalogImportError, match="batch_id"):
        await import_catalog(container, write_document(tmp_path, duplicate_batch, "v2.json"))
    with pytest.raises(CatalogImportError, match="Unknown"):
        await activate_catalog_release(container, "missing")

    assert (await client.get("/api/v1/catalog/snapshot")).json()["catalog_version"] == "v1"


async def test_parallel_postgres_imports_leave_one_complete_active_release(
    postgres_app_client, tmp_path
) -> None:
    client, container = postgres_app_client
    first = demo_document(catalog_version="parallel-v1")
    second = demo_document(
        catalog_version="parallel-v2",
        published_at=datetime(2026, 7, 12, tzinfo=UTC) + timedelta(seconds=1),
    )
    paths = [
        write_document(tmp_path, first, "parallel-v1.json"),
        write_document(tmp_path, second, "parallel-v2.json"),
    ]

    await asyncio.gather(*(import_catalog(container, path) for path in paths))

    snapshot = (await client.get("/api/v1/catalog/snapshot")).json()
    assert snapshot["catalog_version"] == "parallel-v2"
    assert snapshot["total"] == len(snapshot["exercises"]) == 1
    async with container.database.session_factory() as session:
        activation = await session.get(CatalogActivationRow, 1)
        assert activation and activation.catalog_version == "parallel-v2"


async def test_parallel_postgres_activations_keep_exactly_one_release_active(
    postgres_app_client, tmp_path
) -> None:
    client, container = postgres_app_client
    first = demo_document(catalog_version="activation-v1")
    second = demo_document(
        catalog_version="activation-v2", published_at=datetime(2026, 7, 13, tzinfo=UTC)
    )
    await import_catalog(container, write_document(tmp_path, first, "activation-v1.json"))
    await import_catalog(container, write_document(tmp_path, second, "activation-v2.json"))

    await asyncio.gather(
        activate_catalog_release(container, "activation-v1"),
        activate_catalog_release(container, "activation-v2"),
    )

    async with container.database.session_factory() as session:
        active = (
            (
                await session.execute(
                    select(CatalogReleaseRow).where(
                        CatalogReleaseRow.status == CatalogReleaseStatus.ACTIVE
                    )
                )
            )
            .scalars()
            .all()
        )
        activation = await session.get(CatalogActivationRow, 1)
        assert len(active) == 1
        assert activation and active[0].catalog_version == activation.catalog_version
    snapshot = (await client.get("/api/v1/catalog/snapshot")).json()
    assert snapshot["catalog_version"] == active[0].catalog_version
    assert snapshot["total"] == len(snapshot["exercises"])
