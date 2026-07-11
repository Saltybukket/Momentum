from datetime import UTC, datetime
from uuid import uuid4

from fitness_platform.domain.enums import CatalogStatus, MuscleRole, TrackingType
from fitness_platform.domain.models import CatalogExercise
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
