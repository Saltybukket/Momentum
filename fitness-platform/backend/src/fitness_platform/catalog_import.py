import argparse
import asyncio
import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any
from uuid import NAMESPACE_URL, uuid5

from fitness_platform.container import AppContainer
from fitness_platform.core.config import get_settings
from fitness_platform.domain.enums import CatalogStatus, MuscleRole, TrackingType
from fitness_platform.domain.models import CatalogExercise
from fitness_platform.infrastructure.uow import SqlAlchemyUnitOfWork


class CatalogImportError(ValueError):
    pass


def _read_document(path: Path) -> object:
    return json.loads(path.read_text(encoding="utf-8"))


def _write_report(path: Path, rendered: str) -> None:
    path.write_text(rendered + "\n", encoding="utf-8")


def _required(record: dict[str, Any], *fields: str) -> None:
    missing = [field for field in fields if not str(record.get(field, "")).strip()]
    if missing:
        raise CatalogImportError(f"Missing required fields: {', '.join(missing)}")


async def import_catalog(container: AppContainer, path: Path) -> dict[str, Any]:
    document = _read_document(path)
    if not isinstance(document, dict):
        raise CatalogImportError("Catalog document must be an object.")
    exercises = document.get("exercises")
    if not isinstance(exercises, list):
        raise CatalogImportError("exercises must be a list.")
    muscle_slugs = {str(item["slug"]) for item in document.get("muscles", [])}
    equipment_slugs = {str(item["slug"]) for item in document.get("equipment", [])}
    seen: set[tuple[str, str]] = set()
    parsed: list[CatalogExercise] = []
    for raw in exercises:
        if not isinstance(raw, dict):
            raise CatalogImportError("Exercise entries must be objects.")
        _required(
            raw,
            "source",
            "external_id",
            "provenance",
            "license_name",
            "license_url",
            "version",
            "status",
            "name",
        )
        key = (str(raw["source"]), str(raw["external_id"]))
        if key in seen:
            raise CatalogImportError(f"Duplicate source/external_id in batch: {key}")
        seen.add(key)
        muscles = [
            (str(item["slug"]), MuscleRole(str(item["role"]))) for item in raw.get("muscles", [])
        ]
        unknown_muscles = {slug for slug, _ in muscles} - muscle_slugs
        unknown_equipment = set(map(str, raw.get("equipment", []))) - equipment_slugs
        if unknown_muscles:
            raise CatalogImportError(f"Unknown muscle references: {sorted(unknown_muscles)}")
        if unknown_equipment:
            raise CatalogImportError(f"Unknown equipment references: {sorted(unknown_equipment)}")
        if not any(role is MuscleRole.PRIMARY for _, role in muscles):
            raise CatalogImportError(f"{key} requires a primary muscle.")
        status = CatalogStatus(str(raw["status"]))
        reviewed = raw.get("reviewed")
        if not isinstance(reviewed, bool):
            raise CatalogImportError(f"{key} reviewed must be boolean.")
        if status is CatalogStatus.PUBLISHED and not reviewed:
            raise CatalogImportError(f"{key} cannot be published before review.")
        now = datetime.now(UTC)
        parsed.append(
            CatalogExercise(
                id=uuid5(NAMESPACE_URL, f"momentum-catalog:{key[0]}:{key[1]}"),
                external_id=key[1],
                source=key[0],
                provenance=str(raw["provenance"]),
                license_name=str(raw["license_name"]),
                license_url=str(raw["license_url"]),
                version=str(raw["version"]),
                status=status,
                reviewed=reviewed,
                name=str(raw["name"]),
                description=str(raw.get("description", "")),
                tracking_type=TrackingType(str(raw.get("tracking_type", "REPS"))),
                muscles=muscles,
                equipment=list(map(str, raw.get("equipment", []))),
                created_at=now,
                updated_at=now,
            )
        )
    created = updated = unchanged = 0

    def content(exercise: CatalogExercise) -> tuple[object, ...]:
        return (
            exercise.provenance,
            exercise.license_name,
            exercise.license_url,
            exercise.version,
            exercise.status,
            exercise.reviewed,
            exercise.name,
            exercise.description,
            exercise.tracking_type,
            exercise.muscles,
            exercise.equipment,
        )

    async with SqlAlchemyUnitOfWork(container.database.session_factory) as uow:
        for exercise in parsed:
            existing = await uow.catalog.find(exercise.source, exercise.external_id)
            if existing is None:
                created += 1
            elif content(existing) == content(exercise):
                unchanged += 1
            else:
                exercise.created_at = existing.created_at
                updated += 1
            await uow.catalog.upsert(exercise)
        await uow.commit()
    return {
        "read": len(exercises),
        "created": created,
        "updated": updated,
        "unchanged": unchanged,
        "skipped": 0,
        "rejected": 0,
        "warnings": [],
        "errors": [],
        "sources": sorted({item.source for item in parsed}),
        "licenses": sorted({item.license_name for item in parsed}),
        "imported_at": datetime.now(UTC).isoformat(),
        "batch_id": str(document.get("batch_id", "unknown")),
    }


async def _run(path: Path, report: Path | None) -> None:
    container = AppContainer.build(get_settings())
    try:
        result = await import_catalog(container, path)
        rendered = json.dumps(result, indent=2, sort_keys=True)
        if report:
            _write_report(report, rendered)
        print(rendered)
    finally:
        await container.redis.close()
        await container.database.dispose()


def run() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    asyncio.run(_run(args.path, args.report))


if __name__ == "__main__":
    run()
