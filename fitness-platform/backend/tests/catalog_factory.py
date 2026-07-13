from datetime import UTC, datetime
from uuid import NAMESPACE_URL, uuid5

from fitness_platform.domain.enums import (
    CatalogReleaseStatus,
    CatalogStatus,
    MuscleRole,
    TrackingType,
)
from fitness_platform.domain.models import CatalogExercise, CatalogRelease
from fitness_platform.infrastructure.catalog_releases import SqlAlchemyReleaseCatalogRepository


def catalog_exercise(
    external_id: str,
    *,
    muscle: str = "legs",
    equipment: str = "none",
    status: CatalogStatus = CatalogStatus.PUBLISHED,
    reviewed: bool = True,
) -> CatalogExercise:
    now = datetime.now(UTC)
    return CatalogExercise(
        id=uuid5(NAMESPACE_URL, f"momentum-catalog:momentum-demo:{external_id}"),
        external_id=external_id,
        source="momentum-demo",
        provenance="Self-authored technical demo.",
        license_name="CC0-1.0",
        license_url="https://creativecommons.org/publicdomain/zero/1.0/",
        version="1",
        status=status,
        reviewed=reviewed,
        name=external_id.title(),
        description="Technical demo content.",
        tracking_type=TrackingType.REPS,
        muscles=[(muscle, MuscleRole.PRIMARY)],
        equipment=[equipment],
        created_at=now,
        updated_at=now,
    )


async def persist_catalog_release(
    container,
    exercises: list[CatalogExercise],
    *,
    catalog_version: str = "test-release-v1",
    published_at: datetime | None = None,
) -> CatalogRelease:
    release = CatalogRelease(
        schema_version="1",
        catalog_version=catalog_version,
        content_hash="sha256:" + uuid5(NAMESPACE_URL, catalog_version).hex * 2,
        published_at=published_at or datetime(2026, 7, 12, tzinfo=UTC),
        batch_id=catalog_version,
        sources=["momentum-demo"],
        licenses=["CC0-1.0"],
        exercise_count=len(exercises),
        status=CatalogReleaseStatus.STAGED,
    )
    muscles = sorted({slug for exercise in exercises for slug, _ in exercise.muscles})
    equipment = sorted({slug for exercise in exercises for slug in exercise.equipment})
    async with container.database.session_factory() as session:
        repository = SqlAlchemyReleaseCatalogRepository(session)
        await repository.lock_release_changes()
        await repository.stage_release(
            release,
            [(slug, slug.replace("-", " ").title()) for slug in muscles],
            [(slug, slug.replace("-", " ").title()) for slug in equipment],
            exercises,
        )
        await repository.activate_release(catalog_version, allow_rollback=True)
        await session.commit()
    return release
