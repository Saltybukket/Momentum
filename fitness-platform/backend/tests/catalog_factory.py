from datetime import UTC, datetime
from uuid import uuid4

from fitness_platform.domain.enums import CatalogStatus, MuscleRole, TrackingType
from fitness_platform.domain.models import CatalogExercise, CatalogRelease
from fitness_platform.infrastructure.repositories import SqlAlchemyCatalogRepository


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
        id=uuid4(),
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


async def persist_catalog_exercise(container, exercise: CatalogExercise) -> CatalogExercise:
    async with container.database.session_factory() as session:
        result = await SqlAlchemyCatalogRepository(session).upsert(exercise)
        await session.commit()
        return result


async def persist_catalog_release(container, exercise_count: int) -> CatalogRelease:
    release = CatalogRelease(
        schema_version="1",
        catalog_version="test-release-v1",
        content_hash="sha256:" + "a" * 64,
        published_at=datetime(2026, 7, 12, tzinfo=UTC),
        batch_id="test-release-v1",
        sources=["momentum-demo"],
        licenses=["CC0-1.0"],
        exercise_count=exercise_count,
        status="PUBLISHED",
    )
    async with container.database.session_factory() as session:
        await SqlAlchemyCatalogRepository(session).upsert_release(release)
        await session.commit()
    return release
