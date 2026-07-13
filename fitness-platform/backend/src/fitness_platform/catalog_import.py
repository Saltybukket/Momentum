import argparse
import asyncio
import hashlib
import ipaddress
import json
import re
from copy import deepcopy
from datetime import UTC, datetime
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit
from uuid import NAMESPACE_URL, uuid5

from jsonschema import Draft202012Validator, FormatChecker
from jsonschema.exceptions import ValidationError
from sqlalchemy.exc import IntegrityError

from fitness_platform.container import AppContainer
from fitness_platform.core.config import get_settings
from fitness_platform.domain.enums import (
    CatalogReleaseStatus,
    CatalogStatus,
    MuscleRole,
    TrackingType,
)
from fitness_platform.domain.models import CatalogExercise, CatalogRelease
from fitness_platform.domain.text import normalize_multiline, normalize_single_line
from fitness_platform.infrastructure.uow import SqlAlchemyUnitOfWork


class CatalogImportError(ValueError):
    pass


SCHEMA_PATH = (
    Path(__file__).resolve().parents[3] / "data" / "schemas" / "catalog-release-v1.schema.json"
)


def canonical_catalog_payload(document: dict[str, Any]) -> dict[str, Any]:
    """Project a release or API snapshot into the cross-platform semantic hash contract."""
    normalized = _normalize_document(document)
    return {
        "schema_version": normalized["schema_version"],
        "catalog_version": normalized["catalog_version"],
        "published_at": normalized["published_at"],
        "batch_id": normalized["batch_id"],
        "muscles": sorted(normalized["muscles"], key=lambda item: item["slug"]),
        "equipment": sorted(normalized["equipment"], key=lambda item: item["slug"]),
        "exercises": sorted(
            normalized["exercises"],
            key=lambda item: (item["source"], item["external_id"], item["id"]),
        ),
    }


def canonical_release_hash(document: dict[str, Any]) -> str:
    hash_payload = canonical_catalog_payload(document)
    canonical = json.dumps(
        hash_payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True
    ).encode("utf-8")
    return f"sha256:{hashlib.sha256(canonical).hexdigest()}"


def _single_line(value: object, field: str) -> str:
    try:
        return normalize_single_line(str(value), field=field)
    except ValueError as exc:
        raise CatalogImportError(f"Invalid single-line {field}: {exc}") from exc


def _multiline(value: object, field: str) -> str:
    try:
        return normalize_multiline(str(value))
    except ValueError as exc:
        raise CatalogImportError(f"Invalid multiline {field}: {exc}") from exc


def _absolute_https(value: object, field: str) -> str:
    url = _single_line(value, field)
    try:
        parsed = urlsplit(url)
        port = parsed.port
    except ValueError as exc:
        raise CatalogImportError(f"{field} must be an absolute https URL.") from exc
    hostname = parsed.hostname or ""
    if (
        parsed.scheme != "https"
        or not hostname
        or parsed.username is not None
        or parsed.password is not None
        or (port is not None and not 1 <= port <= 65535)
        or not _valid_hostname(hostname)
    ):
        raise CatalogImportError(f"{field} must be an absolute https URL.")
    return url


def _valid_hostname(hostname: str) -> bool:
    try:
        ipaddress.ip_address(hostname)
        return True
    except ValueError:
        pass
    try:
        ascii_hostname = hostname.encode("idna").decode("ascii")
    except UnicodeError:
        return False
    if len(ascii_hostname) > 253:
        return False
    return all(
        re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?", label)
        for label in ascii_hostname.removesuffix(".").split(".")
    )


def _normalize_document(document: dict[str, Any]) -> dict[str, Any]:
    normalized = deepcopy(document)
    normalized["schema_version"] = _single_line(normalized["schema_version"], "schema_version")
    normalized["catalog_version"] = _single_line(normalized["catalog_version"], "catalog_version")
    normalized["batch_id"] = _single_line(normalized["batch_id"], "batch_id")
    published_at = datetime.fromisoformat(str(normalized["published_at"]).replace("Z", "+00:00"))
    if published_at.tzinfo is None:
        raise CatalogImportError("published_at must include a timezone.")
    normalized["published_at"] = published_at.astimezone(UTC).isoformat().replace("+00:00", "Z")
    for facet_name in ("muscles", "equipment"):
        for facet in normalized[facet_name]:
            facet["slug"] = _single_line(facet["slug"], f"{facet_name}.slug")
            facet["name"] = _single_line(facet["name"], f"{facet_name}.name")
    for exercise in normalized["exercises"]:
        for field in (
            "id",
            "source",
            "external_id",
            "version",
            "name",
            "tracking_type",
            "status",
            "license_name",
        ):
            exercise[field] = _single_line(exercise[field], f"exercise.{field}")
        exercise["description"] = _multiline(exercise["description"], "exercise.description")
        exercise["provenance"] = _multiline(exercise["provenance"], "exercise.provenance")
        exercise["license_url"] = _absolute_https(exercise["license_url"], "exercise.license_url")
        for relation in exercise["muscles"]:
            relation["slug"] = _single_line(relation["slug"], "exercise.muscles.slug")
            relation["role"] = _single_line(relation["role"], "exercise.muscles.role")
        exercise["muscles"] = sorted(
            exercise["muscles"], key=lambda item: (item["slug"], item["role"])
        )
        exercise["equipment"] = sorted(
            _single_line(slug, "exercise.equipment") for slug in exercise["equipment"]
        )
    return normalized


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
    try:
        schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
        Draft202012Validator(schema, format_checker=FormatChecker()).validate(document)
    except (OSError, json.JSONDecodeError, ValidationError) as exc:
        raise CatalogImportError(f"Catalog schema validation failed: {exc}") from exc
    document = _normalize_document(document)
    declared_hash = document.get("content_hash")
    computed_hash = canonical_release_hash(document)
    if declared_hash != computed_hash:
        raise CatalogImportError(
            f"Catalog content_hash mismatch: expected {computed_hash}, got {declared_hash}."
        )
    exercises = document.get("exercises")
    if not isinstance(exercises, list):
        raise CatalogImportError("exercises must be a list.")
    raw_muscles = document.get("muscles", [])
    raw_equipment = document.get("equipment", [])
    if not isinstance(raw_muscles, list) or not isinstance(raw_equipment, list):
        raise CatalogImportError("muscles and equipment must be lists.")
    muscles_catalog = [(str(item["slug"]), str(item["name"])) for item in raw_muscles]
    equipment_catalog = [(str(item["slug"]), str(item["name"])) for item in raw_equipment]
    if len({slug for slug, _ in muscles_catalog}) != len(muscles_catalog):
        raise CatalogImportError("Duplicate muscle slug in batch.")
    if len({slug for slug, _ in equipment_catalog}) != len(equipment_catalog):
        raise CatalogImportError("Duplicate equipment slug in batch.")
    muscle_slugs = {slug for slug, _ in muscles_catalog}
    equipment_slugs = {slug for slug, _ in equipment_catalog}
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
        if len({slug for slug, _ in muscles}) != len(muscles):
            raise CatalogImportError(f"{key} contains duplicate muscle relations.")
        equipment = list(map(str, raw.get("equipment", [])))
        if len(set(equipment)) != len(equipment):
            raise CatalogImportError(f"{key} contains duplicate equipment relations.")
        unknown_muscles = {slug for slug, _ in muscles} - muscle_slugs
        unknown_equipment = set(equipment) - equipment_slugs
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
        if status is not CatalogStatus.PUBLISHED or not reviewed:
            raise CatalogImportError(f"{key} is not approved for a public release.")
        license_url = str(raw["license_url"])
        now = datetime.now(UTC)
        stable_id = uuid5(NAMESPACE_URL, f"momentum-catalog:{key[0]}:{key[1]}")
        if str(raw["id"]) != str(stable_id):
            raise CatalogImportError(f"{key} id does not match its stable source/external_id.")
        parsed.append(
            CatalogExercise(
                id=stable_id,
                external_id=key[1],
                source=key[0],
                provenance=str(raw["provenance"]),
                license_name=str(raw["license_name"]),
                license_url=license_url,
                version=str(raw["version"]),
                status=status,
                reviewed=reviewed,
                name=str(raw["name"]),
                description=str(raw.get("description", "")),
                tracking_type=TrackingType(str(raw.get("tracking_type", "REPS"))),
                muscles=muscles,
                equipment=equipment,
                created_at=now,
                updated_at=now,
            )
        )
    sources = sorted({item.source for item in parsed})
    licenses = sorted({item.license_name for item in parsed})
    release = CatalogRelease(
        schema_version=str(document["schema_version"]),
        catalog_version=str(document["catalog_version"]),
        content_hash=computed_hash,
        published_at=datetime.fromisoformat(str(document["published_at"]).replace("Z", "+00:00")),
        batch_id=str(document["batch_id"]),
        sources=sources,
        licenses=licenses,
        exercise_count=len(parsed),
        status=CatalogReleaseStatus.STAGED,
    )
    try:
        async with SqlAlchemyUnitOfWork(container.database.session_factory) as uow:
            await uow.catalog.lock_release_changes()
            existing = await uow.catalog.get_release(release.catalog_version)
            if existing:
                if existing.content_hash != release.content_hash:
                    raise CatalogImportError(
                        "catalog_version is immutable and already has a different content_hash."
                    )
                return _report(document, release, unchanged=len(parsed), activated=False)
            conflict = await uow.catalog.identity_conflict(
                content_hash=release.content_hash,
                batch_id=release.batch_id,
                catalog_version=release.catalog_version,
            )
            if conflict:
                raise CatalogImportError(conflict)
            await uow.catalog.stage_release(release, muscles_catalog, equipment_catalog, parsed)
            activated = await uow.catalog.activate_release(
                release.catalog_version, allow_rollback=False
            )
            await uow.commit()
    except IntegrityError as exc:
        raise CatalogImportError(
            "Catalog release conflicts with an immutable stored identity."
        ) from exc
    return _report(document, release, created=len(parsed), activated=activated)


def _report(
    document: dict[str, Any],
    release: CatalogRelease,
    *,
    created: int = 0,
    unchanged: int = 0,
    activated: bool,
) -> dict[str, Any]:
    return {
        "read": release.exercise_count,
        "created": created,
        "updated": 0,
        "unchanged": unchanged,
        "skipped": 0,
        "rejected": 0,
        "warnings": [],
        "errors": [],
        "sources": release.sources,
        "licenses": release.licenses,
        "schema_version": release.schema_version,
        "catalog_version": release.catalog_version,
        "content_hash": release.content_hash,
        "imported_at": datetime.now(UTC).isoformat(),
        "batch_id": str(document.get("batch_id", "unknown")),
        "activated": activated,
        "release_status": (
            CatalogReleaseStatus.ACTIVE.value
            if activated
            else (CatalogReleaseStatus.RETIRED.value if created else "UNCHANGED")
        ),
    }


async def activate_catalog_release(container: AppContainer, catalog_version: str) -> dict[str, Any]:
    async with SqlAlchemyUnitOfWork(container.database.session_factory) as uow:
        await uow.catalog.lock_release_changes()
        release = await uow.catalog.get_release(catalog_version)
        if release is None:
            raise CatalogImportError(f"Unknown catalog release: {catalog_version}")
        activated = await uow.catalog.activate_release(catalog_version, allow_rollback=True)
        await uow.commit()
    return {
        "catalog_version": catalog_version,
        "content_hash": release.content_hash,
        "activated": activated,
        "release_status": CatalogReleaseStatus.ACTIVE.value,
    }


async def _run(path: Path | None, report: Path | None, activate_version: str | None) -> None:
    container = AppContainer.build(get_settings())
    try:
        if activate_version:
            result = await activate_catalog_release(container, activate_version)
        elif path:
            result = await import_catalog(container, path)
        else:
            raise CatalogImportError("A catalog path or --activate-version is required.")
        rendered = json.dumps(result, indent=2, sort_keys=True)
        if report:
            _write_report(report, rendered)
        print(rendered)
    finally:
        await container.redis.close()
        await container.database.dispose()


def run() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", type=Path, nargs="?")
    parser.add_argument("--report", type=Path)
    parser.add_argument("--activate-version")
    args = parser.parse_args()
    asyncio.run(_run(args.path, args.report, args.activate_version))


if __name__ == "__main__":
    run()
