import builtins
from collections.abc import Sequence
from uuid import UUID

from sqlalchemy import func, select, text
from sqlalchemy.ext.asyncio import AsyncSession

from fitness_platform.domain.enums import CatalogReleaseStatus, CatalogStatus, MuscleRole
from fitness_platform.domain.models import CatalogExercise, CatalogRelease
from fitness_platform.infrastructure.orm import (
    CatalogActivationRow,
    CatalogReleaseEquipmentRow,
    CatalogReleaseExerciseEquipmentRow,
    CatalogReleaseExerciseMuscleRow,
    CatalogReleaseExerciseRow,
    CatalogReleaseMuscleRow,
    CatalogReleaseRow,
)


class SqlAlchemyReleaseCatalogRepository:
    """Immutable release storage with one transactionally selected active snapshot."""

    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def lock_release_changes(self) -> None:
        if self._session.bind and self._session.bind.dialect.name == "postgresql":
            await self._session.execute(
                text("SELECT pg_advisory_xact_lock(hashtext('momentum-catalog-release'))")
            )

    async def get_release(self, catalog_version: str) -> CatalogRelease | None:
        row = await self._session.get(CatalogReleaseRow, catalog_version)
        return self._release_from_row(row) if row else None

    async def identity_conflict(
        self, *, content_hash: str, batch_id: str, catalog_version: str
    ) -> str | None:
        hash_owner = await self._session.scalar(
            select(CatalogReleaseRow.catalog_version).where(
                CatalogReleaseRow.content_hash == content_hash,
                CatalogReleaseRow.catalog_version != catalog_version,
            )
        )
        if hash_owner:
            return f"content_hash already belongs to catalog_version {hash_owner}"
        batch_owner = await self._session.scalar(
            select(CatalogReleaseRow.catalog_version).where(
                CatalogReleaseRow.batch_id == batch_id,
                CatalogReleaseRow.catalog_version != catalog_version,
            )
        )
        if batch_owner:
            return f"batch_id already belongs to catalog_version {batch_owner}"
        return None

    async def stage_release(
        self,
        release: CatalogRelease,
        muscles: Sequence[tuple[str, str]],
        equipment: Sequence[tuple[str, str]],
        exercises: Sequence[CatalogExercise],
    ) -> None:
        version = release.catalog_version
        self._session.add(
            CatalogReleaseRow(
                catalog_version=version,
                schema_version=release.schema_version,
                content_hash=release.content_hash,
                published_at=release.published_at,
                batch_id=release.batch_id,
                sources=release.sources,
                licenses=release.licenses,
                exercise_count=release.exercise_count,
                status=CatalogReleaseStatus.STAGED,
            )
        )
        await self._session.flush()
        self._session.add_all(
            CatalogReleaseMuscleRow(catalog_version=version, slug=slug, name=name)
            for slug, name in muscles
        )
        self._session.add_all(
            CatalogReleaseEquipmentRow(catalog_version=version, slug=slug, name=name)
            for slug, name in equipment
        )
        await self._session.flush()
        for exercise in exercises:
            self._session.add(
                CatalogReleaseExerciseRow(
                    catalog_version=version,
                    id=exercise.id,
                    external_id=exercise.external_id,
                    source=exercise.source,
                    provenance=exercise.provenance,
                    license_name=exercise.license_name,
                    license_url=exercise.license_url,
                    version=exercise.version,
                    status=exercise.status,
                    reviewed=exercise.reviewed,
                    name=exercise.name,
                    description=exercise.description,
                    tracking_type=exercise.tracking_type,
                    created_at=exercise.created_at,
                    updated_at=exercise.updated_at,
                )
            )
        await self._session.flush()
        for exercise in exercises:
            self._session.add_all(
                CatalogReleaseExerciseMuscleRow(
                    catalog_version=version,
                    exercise_id=exercise.id,
                    muscle_slug=slug,
                    role=role,
                )
                for slug, role in exercise.muscles
            )
            self._session.add_all(
                CatalogReleaseExerciseEquipmentRow(
                    catalog_version=version,
                    exercise_id=exercise.id,
                    equipment_slug=slug,
                )
                for slug in exercise.equipment
            )
        await self._session.flush()

    async def activate_release(self, catalog_version: str, *, allow_rollback: bool) -> bool:
        activation = (
            await self._session.execute(
                select(CatalogActivationRow)
                .where(CatalogActivationRow.singleton_id == 1)
                .with_for_update()
            )
        ).scalar_one_or_none()
        if activation is None:
            activation = CatalogActivationRow(singleton_id=1, catalog_version=None)
            self._session.add(activation)
            await self._session.flush()
        target = await self._session.get(CatalogReleaseRow, catalog_version)
        if target is None:
            raise LookupError(f"Unknown catalog release: {catalog_version}")
        if activation.catalog_version == catalog_version:
            return False
        active = (
            await self._session.get(CatalogReleaseRow, activation.catalog_version)
            if activation.catalog_version
            else None
        )
        if active and not allow_rollback and target.published_at < active.published_at:
            target.status = CatalogReleaseStatus.RETIRED
            await self._session.flush()
            return False
        if active:
            active.status = CatalogReleaseStatus.RETIRED
            await self._session.flush()
        target.status = CatalogReleaseStatus.ACTIVE
        activation.catalog_version = catalog_version
        await self._session.flush()
        return True

    async def get_active_release(self) -> CatalogRelease | None:
        row = (
            await self._session.execute(
                select(CatalogReleaseRow)
                .join(
                    CatalogActivationRow,
                    CatalogActivationRow.catalog_version == CatalogReleaseRow.catalog_version,
                )
                .where(CatalogActivationRow.singleton_id == 1)
            )
        ).scalar_one_or_none()
        return self._release_from_row(row) if row else None

    async def list(
        self,
        catalog_version: str,
        muscle: str | None,
        equipment: str | None,
        query: str | None = None,
        limit: int | None = 100,
        offset: int = 0,
    ) -> Sequence[CatalogExercise]:
        statement = select(CatalogReleaseExerciseRow).where(
            CatalogReleaseExerciseRow.catalog_version == catalog_version,
            CatalogReleaseExerciseRow.status == CatalogStatus.PUBLISHED,
            CatalogReleaseExerciseRow.reviewed.is_(True),
        )
        if muscle:
            statement = statement.join(
                CatalogReleaseExerciseMuscleRow,
                (CatalogReleaseExerciseMuscleRow.catalog_version == catalog_version)
                & (CatalogReleaseExerciseMuscleRow.exercise_id == CatalogReleaseExerciseRow.id),
            ).where(CatalogReleaseExerciseMuscleRow.muscle_slug == muscle)
        if equipment:
            statement = statement.join(
                CatalogReleaseExerciseEquipmentRow,
                (CatalogReleaseExerciseEquipmentRow.catalog_version == catalog_version)
                & (CatalogReleaseExerciseEquipmentRow.exercise_id == CatalogReleaseExerciseRow.id),
            ).where(CatalogReleaseExerciseEquipmentRow.equipment_slug == equipment)
        if query and query.strip():
            escaped = self._escape_like(query.strip())
            statement = statement.where(
                CatalogReleaseExerciseRow.name.ilike(f"%{escaped}%", escape="\\")
            )
        statement = statement.order_by(
            CatalogReleaseExerciseRow.name, CatalogReleaseExerciseRow.id
        ).offset(offset)
        if limit is not None:
            statement = statement.limit(limit)
        rows = (await self._session.execute(statement.distinct())).scalars().all()
        return await self._rows_to_models(catalog_version, rows)

    async def count(
        self,
        catalog_version: str,
        muscle: str | None,
        equipment: str | None,
        query: str | None,
    ) -> int:
        statement = select(func.count(func.distinct(CatalogReleaseExerciseRow.id))).where(
            CatalogReleaseExerciseRow.catalog_version == catalog_version,
            CatalogReleaseExerciseRow.status == CatalogStatus.PUBLISHED,
            CatalogReleaseExerciseRow.reviewed.is_(True),
        )
        if muscle:
            statement = statement.join(
                CatalogReleaseExerciseMuscleRow,
                (CatalogReleaseExerciseMuscleRow.catalog_version == catalog_version)
                & (CatalogReleaseExerciseMuscleRow.exercise_id == CatalogReleaseExerciseRow.id),
            ).where(CatalogReleaseExerciseMuscleRow.muscle_slug == muscle)
        if equipment:
            statement = statement.join(
                CatalogReleaseExerciseEquipmentRow,
                (CatalogReleaseExerciseEquipmentRow.catalog_version == catalog_version)
                & (CatalogReleaseExerciseEquipmentRow.exercise_id == CatalogReleaseExerciseRow.id),
            ).where(CatalogReleaseExerciseEquipmentRow.equipment_slug == equipment)
        if query and query.strip():
            escaped = self._escape_like(query.strip())
            statement = statement.where(
                CatalogReleaseExerciseRow.name.ilike(f"%{escaped}%", escape="\\")
            )
        return int((await self._session.scalar(statement)) or 0)

    async def get(self, catalog_version: str, exercise_id: UUID) -> CatalogExercise | None:
        row = await self._session.get(CatalogReleaseExerciseRow, (catalog_version, exercise_id))
        if row is None or row.status != CatalogStatus.PUBLISHED or not row.reviewed:
            return None
        return (await self._rows_to_models(catalog_version, [row]))[0]

    async def list_muscles(self, catalog_version: str) -> Sequence[tuple[str, str]]:
        rows = (
            await self._session.execute(
                select(CatalogReleaseMuscleRow.slug, CatalogReleaseMuscleRow.name)
                .where(CatalogReleaseMuscleRow.catalog_version == catalog_version)
                .order_by(CatalogReleaseMuscleRow.name)
            )
        ).all()
        return [(str(slug), str(name)) for slug, name in rows]

    async def list_equipment(self, catalog_version: str) -> Sequence[tuple[str, str]]:
        rows = (
            await self._session.execute(
                select(CatalogReleaseEquipmentRow.slug, CatalogReleaseEquipmentRow.name)
                .where(CatalogReleaseEquipmentRow.catalog_version == catalog_version)
                .order_by(CatalogReleaseEquipmentRow.name)
            )
        ).all()
        return [(str(slug), str(name)) for slug, name in rows]

    async def _rows_to_models(
        self, catalog_version: str, rows: Sequence[CatalogReleaseExerciseRow]
    ) -> builtins.list[CatalogExercise]:
        if not rows:
            return []
        ids = [row.id for row in rows]
        muscle_rows = (
            await self._session.execute(
                select(
                    CatalogReleaseExerciseMuscleRow.exercise_id,
                    CatalogReleaseExerciseMuscleRow.muscle_slug,
                    CatalogReleaseExerciseMuscleRow.role,
                ).where(
                    CatalogReleaseExerciseMuscleRow.catalog_version == catalog_version,
                    CatalogReleaseExerciseMuscleRow.exercise_id.in_(ids),
                )
            )
        ).all()
        equipment_rows = (
            await self._session.execute(
                select(
                    CatalogReleaseExerciseEquipmentRow.exercise_id,
                    CatalogReleaseExerciseEquipmentRow.equipment_slug,
                ).where(
                    CatalogReleaseExerciseEquipmentRow.catalog_version == catalog_version,
                    CatalogReleaseExerciseEquipmentRow.exercise_id.in_(ids),
                )
            )
        ).all()
        muscles: dict[UUID, list[tuple[str, MuscleRole]]] = {row.id: [] for row in rows}
        equipment: dict[UUID, list[str]] = {row.id: [] for row in rows}
        for exercise_id, slug, role in muscle_rows:
            muscles[exercise_id].append((str(slug), MuscleRole(role)))
        for exercise_id, slug in equipment_rows:
            equipment[exercise_id].append(str(slug))
        return [
            CatalogExercise(
                id=row.id,
                external_id=row.external_id,
                source=row.source,
                provenance=row.provenance,
                license_name=row.license_name,
                license_url=row.license_url,
                version=row.version,
                status=CatalogStatus(row.status),
                reviewed=row.reviewed,
                name=row.name,
                description=row.description,
                tracking_type=row.tracking_type,
                muscles=sorted(muscles[row.id], key=lambda item: (item[0], item[1].value)),
                equipment=sorted(equipment[row.id]),
                created_at=row.created_at,
                updated_at=row.updated_at,
            )
            for row in rows
        ]

    @staticmethod
    def _release_from_row(row: CatalogReleaseRow) -> CatalogRelease:
        return CatalogRelease(
            schema_version=row.schema_version,
            catalog_version=row.catalog_version,
            content_hash=row.content_hash,
            published_at=row.published_at,
            batch_id=row.batch_id,
            sources=list(row.sources),
            licenses=list(row.licenses),
            exercise_count=row.exercise_count,
            status=CatalogReleaseStatus(row.status),
        )

    @staticmethod
    def _escape_like(value: str) -> str:
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
