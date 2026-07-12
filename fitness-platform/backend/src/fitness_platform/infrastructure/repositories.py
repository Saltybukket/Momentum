from collections.abc import Sequence
from datetime import UTC, datetime
from uuid import UUID, uuid4

from sqlalchemy import delete, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from fitness_platform.domain.enums import CatalogStatus, MuscleRole, UserKind
from fitness_platform.domain.models import (
    CatalogExercise,
    Exercise,
    GuestSession,
    Profile,
    Workout,
    WorkoutExercise,
)
from fitness_platform.infrastructure.orm import (
    CatalogExerciseEquipmentRow,
    CatalogExerciseMuscleRow,
    CatalogExerciseRow,
    EquipmentRow,
    ExerciseChangeRow,
    ExerciseRow,
    GuestSessionRow,
    IdempotencyRecordRow,
    MuscleRow,
    OutboxEventRow,
    ProfileRow,
    UserRow,
    WorkoutExerciseRow,
    WorkoutRow,
)


class SqlAlchemyCatalogRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def list(
        self,
        muscle: str | None,
        equipment: str | None,
        query: str | None = None,
        limit: int = 100,
        offset: int = 0,
    ) -> Sequence[CatalogExercise]:
        statement = select(CatalogExerciseRow).where(
            CatalogExerciseRow.status == CatalogStatus.PUBLISHED,
            CatalogExerciseRow.reviewed.is_(True),
        )
        if muscle:
            statement = (
                statement.join(CatalogExerciseMuscleRow)
                .join(MuscleRow)
                .where(MuscleRow.slug == muscle)
            )
        if equipment:
            statement = (
                statement.join(CatalogExerciseEquipmentRow)
                .join(EquipmentRow)
                .where(EquipmentRow.slug == equipment)
            )
        if query:
            statement = statement.where(CatalogExerciseRow.name.ilike(f"%{query.strip()}%"))
        statement = (
            statement.order_by(CatalogExerciseRow.name, CatalogExerciseRow.id)
            .limit(limit)
            .offset(offset)
        )
        rows = (await self._session.execute(statement.distinct())).scalars().all()
        if not rows:
            return []
        ids = [row.id for row in rows]
        muscle_rows = (
            await self._session.execute(
                select(
                    CatalogExerciseMuscleRow.exercise_id,
                    MuscleRow.slug,
                    CatalogExerciseMuscleRow.role,
                )
                .join(MuscleRow)
                .where(CatalogExerciseMuscleRow.exercise_id.in_(ids))
            )
        ).all()
        equipment_rows = (
            await self._session.execute(
                select(CatalogExerciseEquipmentRow.exercise_id, EquipmentRow.slug)
                .join(EquipmentRow)
                .where(CatalogExerciseEquipmentRow.exercise_id.in_(ids))
            )
        ).all()
        muscles_by_id: dict[UUID, list[tuple[str, MuscleRole]]] = {item: [] for item in ids}
        equipment_by_id: dict[UUID, list[str]] = {item: [] for item in ids}
        for exercise_id, slug, role in muscle_rows:
            muscles_by_id[exercise_id].append((str(slug), MuscleRole(role)))
        for exercise_id, slug in equipment_rows:
            equipment_by_id[exercise_id].append(str(slug))
        return [
            self._row_to_model(row, muscles_by_id[row.id], equipment_by_id[row.id]) for row in rows
        ]

    async def get(self, exercise_id: UUID) -> CatalogExercise | None:
        row = await self._session.get(CatalogExerciseRow, exercise_id)
        if row is None or row.status != CatalogStatus.PUBLISHED or not row.reviewed:
            return None
        return await self._to_model(row)

    async def find(self, source: str, external_id: str) -> CatalogExercise | None:
        row = (
            await self._session.execute(
                select(CatalogExerciseRow).where(
                    CatalogExerciseRow.source == source,
                    CatalogExerciseRow.external_id == external_id,
                )
            )
        ).scalar_one_or_none()
        return await self._to_model(row) if row else None

    async def list_muscles(self) -> Sequence[tuple[str, str]]:
        rows = (
            await self._session.execute(
                select(MuscleRow.slug, MuscleRow.name)
                .join(CatalogExerciseMuscleRow)
                .join(CatalogExerciseRow)
                .where(
                    CatalogExerciseRow.status == CatalogStatus.PUBLISHED,
                    CatalogExerciseRow.reviewed.is_(True),
                )
                .distinct()
                .order_by(MuscleRow.name)
            )
        ).all()
        return [(slug, name) for slug, name in rows]

    async def list_equipment(self) -> Sequence[tuple[str, str]]:
        rows = (
            await self._session.execute(
                select(EquipmentRow.slug, EquipmentRow.name)
                .join(CatalogExerciseEquipmentRow)
                .join(CatalogExerciseRow)
                .where(
                    CatalogExerciseRow.status == CatalogStatus.PUBLISHED,
                    CatalogExerciseRow.reviewed.is_(True),
                )
                .distinct()
                .order_by(EquipmentRow.name)
            )
        ).all()
        return [(slug, name) for slug, name in rows]

    async def upsert(self, exercise: CatalogExercise) -> CatalogExercise:
        row = (
            await self._session.execute(
                select(CatalogExerciseRow).where(
                    CatalogExerciseRow.source == exercise.source,
                    CatalogExerciseRow.external_id == exercise.external_id,
                )
            )
        ).scalar_one_or_none()
        if row is None:
            row = CatalogExerciseRow(
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
            self._session.add(row)
        else:
            for key in (
                "provenance",
                "license_name",
                "license_url",
                "version",
                "status",
                "reviewed",
                "name",
                "description",
                "tracking_type",
                "updated_at",
            ):
                setattr(row, key, getattr(exercise, key))
            await self._session.execute(
                delete(CatalogExerciseMuscleRow).where(
                    CatalogExerciseMuscleRow.exercise_id == row.id
                )
            )
            await self._session.execute(
                delete(CatalogExerciseEquipmentRow).where(
                    CatalogExerciseEquipmentRow.exercise_id == row.id
                )
            )
        for slug, role in exercise.muscles:
            muscle = (
                await self._session.execute(select(MuscleRow).where(MuscleRow.slug == slug))
            ).scalar_one_or_none()
            if muscle is None:
                muscle = MuscleRow(id=uuid4(), slug=slug, name=slug.replace("-", " ").title())
                self._session.add(muscle)
                await self._session.flush()
            self._session.add(
                CatalogExerciseMuscleRow(exercise_id=row.id, muscle_id=muscle.id, role=role)
            )
        for slug in exercise.equipment:
            item = (
                await self._session.execute(select(EquipmentRow).where(EquipmentRow.slug == slug))
            ).scalar_one_or_none()
            if item is None:
                item = EquipmentRow(id=uuid4(), slug=slug, name=slug.replace("-", " ").title())
                self._session.add(item)
                await self._session.flush()
            self._session.add(CatalogExerciseEquipmentRow(exercise_id=row.id, equipment_id=item.id))
        await self._session.flush()
        return await self._to_model(row)

    async def upsert_muscles(self, values: Sequence[tuple[str, str]]) -> None:
        for slug, name in values:
            row = (
                await self._session.execute(select(MuscleRow).where(MuscleRow.slug == slug))
            ).scalar_one_or_none()
            if row is None:
                self._session.add(MuscleRow(id=uuid4(), slug=slug, name=name))
            else:
                row.name = name
        await self._session.flush()

    async def upsert_equipment(self, values: Sequence[tuple[str, str]]) -> None:
        for slug, name in values:
            row = (
                await self._session.execute(select(EquipmentRow).where(EquipmentRow.slug == slug))
            ).scalar_one_or_none()
            if row is None:
                self._session.add(EquipmentRow(id=uuid4(), slug=slug, name=name))
            else:
                row.name = name
        await self._session.flush()

    async def _to_model(self, row: CatalogExerciseRow) -> CatalogExercise:
        muscles = (
            await self._session.execute(
                select(MuscleRow.slug, CatalogExerciseMuscleRow.role)
                .join(CatalogExerciseMuscleRow)
                .where(CatalogExerciseMuscleRow.exercise_id == row.id)
            )
        ).all()
        equipment = (
            (
                await self._session.execute(
                    select(EquipmentRow.slug)
                    .join(CatalogExerciseEquipmentRow)
                    .where(CatalogExerciseEquipmentRow.exercise_id == row.id)
                )
            )
            .scalars()
            .all()
        )
        return self._row_to_model(
            row, [(str(slug), MuscleRole(role)) for slug, role in muscles], list(equipment)
        )

    @staticmethod
    def _row_to_model(
        row: CatalogExerciseRow,
        muscles: Sequence[tuple[str, MuscleRole]],
        equipment: Sequence[str],
    ) -> CatalogExercise:
        return CatalogExercise(
            row.id,
            row.external_id,
            row.source,
            row.provenance,
            row.license_name,
            row.license_url,
            row.version,
            CatalogStatus(row.status),
            row.reviewed,
            row.name,
            row.description,
            row.tracking_type,
            list(muscles),
            list(equipment),
            row.created_at,
            row.updated_at,
        )


def profile_from_row(row: ProfileRow) -> Profile:
    return Profile(
        user_id=row.user_id,
        display_name=row.display_name,
        unit_system=row.unit_system,
        onboarding_status=row.onboarding_status,
        sync_status=row.sync_status,
        created_at=row.created_at,
        updated_at=row.updated_at,
    )


def exercise_from_row(row: ExerciseRow) -> Exercise:
    return Exercise(
        id=row.id,
        owner_user_id=row.owner_user_id,
        name=row.name,
        description=row.description,
        primary_muscle_group=row.primary_muscle_group,
        equipment=row.equipment,
        tracking_type=row.tracking_type,
        notes=row.notes,
        sync_status=row.sync_status,
        created_at=row.created_at,
        updated_at=row.updated_at,
        server_updated_at=row.server_updated_at,
        deleted_at=row.deleted_at,
        revision=row.revision,
    )


def workout_from_row(row: WorkoutRow) -> Workout:
    return Workout(
        id=row.id,
        owner_user_id=row.owner_user_id,
        title=row.title,
        status=row.status,
        start_time=row.start_time,
        end_time=row.end_time,
        notes=row.notes,
        sync_status=row.sync_status,
        created_at=row.created_at,
        updated_at=row.updated_at,
        server_updated_at=row.server_updated_at,
        exercises=[
            WorkoutExercise(
                id=link.id,
                workout_id=link.workout_id,
                exercise_id=link.exercise_id,
                position=link.position,
            )
            for link in row.exercise_links
        ],
    )


class SqlAlchemyUserRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def create_guest(self, user_id: UUID, created_at: datetime) -> None:
        self._session.add(UserRow(id=user_id, kind=UserKind.GUEST, created_at=created_at))
        await self._session.flush()

    async def exists(self, user_id: UUID) -> bool:
        return (await self._session.get(UserRow, user_id)) is not None


class SqlAlchemyGuestSessionRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def add(self, session: GuestSession) -> None:
        self._session.add(
            GuestSessionRow(
                id=session.id,
                user_id=session.user_id,
                token_hash=session.token_hash,
                expires_at=session.expires_at,
                created_at=session.created_at,
                revoked_at=session.revoked_at,
                installation_id=session.installation_id,
                recovery_secret_hash=session.recovery_secret_hash,
            )
        )
        await self._session.flush()

    async def find_active_by_token_hash(
        self, token_hash: str, now: datetime
    ) -> GuestSession | None:
        statement = select(GuestSessionRow).where(
            GuestSessionRow.token_hash == token_hash,
            GuestSessionRow.revoked_at.is_(None),
            GuestSessionRow.expires_at > now,
        )
        row = (await self._session.execute(statement)).scalar_one_or_none()
        if row is None:
            return None
        return GuestSession(
            id=row.id,
            user_id=row.user_id,
            token_hash=row.token_hash,
            expires_at=row.expires_at,
            created_at=row.created_at,
            revoked_at=row.revoked_at,
            installation_id=row.installation_id,
            recovery_secret_hash=row.recovery_secret_hash,
        )

    async def find_by_installation(self, installation_id: UUID) -> GuestSession | None:
        row = (
            await self._session.execute(
                select(GuestSessionRow).where(GuestSessionRow.installation_id == installation_id)
            )
        ).scalar_one_or_none()
        return self._to_model(row) if row else None

    async def update_credentials(self, session: GuestSession) -> None:
        row = await self._session.get(GuestSessionRow, session.id)
        if row is None:
            raise RuntimeError("Guest session disappeared during credential rotation.")
        row.token_hash = session.token_hash
        row.expires_at = session.expires_at
        row.revoked_at = session.revoked_at
        row.recovery_secret_hash = session.recovery_secret_hash
        await self._session.flush()

    @staticmethod
    def _to_model(row: GuestSessionRow) -> GuestSession:
        return GuestSession(
            id=row.id,
            user_id=row.user_id,
            token_hash=row.token_hash,
            expires_at=row.expires_at,
            created_at=row.created_at,
            revoked_at=row.revoked_at,
            installation_id=row.installation_id,
            recovery_secret_hash=row.recovery_secret_hash,
        )


class SqlAlchemyProfileRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def get(self, user_id: UUID) -> Profile | None:
        row = await self._session.get(ProfileRow, user_id)
        return profile_from_row(row) if row else None

    async def upsert(self, profile: Profile) -> Profile:
        row = await self._session.get(ProfileRow, profile.user_id)
        if row is None:
            row = ProfileRow(
                user_id=profile.user_id,
                display_name=profile.display_name,
                unit_system=profile.unit_system,
                onboarding_status=profile.onboarding_status,
                sync_status=profile.sync_status,
                created_at=profile.created_at,
                updated_at=profile.updated_at,
            )
            self._session.add(row)
        else:
            row.display_name = profile.display_name
            row.unit_system = profile.unit_system
            row.onboarding_status = profile.onboarding_status
            row.sync_status = profile.sync_status
            row.updated_at = profile.updated_at
        await self._session.flush()
        return profile_from_row(row)


class SqlAlchemyExerciseRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def is_id_taken(self, exercise_id: UUID) -> bool:
        return await self._session.get(ExerciseRow, exercise_id) is not None

    async def list(self, user_id: UUID) -> Sequence[Exercise]:
        rows = (
            await self._session.execute(
                select(ExerciseRow)
                .where(ExerciseRow.owner_user_id == user_id, ExerciseRow.deleted_at.is_(None))
                .order_by(ExerciseRow.updated_at.desc())
            )
        ).scalars()
        return [exercise_from_row(row) for row in rows]

    async def get(self, user_id: UUID, exercise_id: UUID) -> Exercise | None:
        row = await self._session.get(ExerciseRow, exercise_id)
        if row is None or row.owner_user_id != user_id or row.deleted_at is not None:
            return None
        return exercise_from_row(row)

    async def get_including_deleted(self, user_id: UUID, exercise_id: UUID) -> Exercise | None:
        row = await self._session.get(ExerciseRow, exercise_id)
        if row is None or row.owner_user_id != user_id:
            return None
        return exercise_from_row(row)

    async def upsert(self, exercise: Exercise) -> Exercise:
        row = await self._session.get(ExerciseRow, exercise.id)
        if row is None:
            row = ExerciseRow(
                id=exercise.id,
                owner_user_id=exercise.owner_user_id,
                name=exercise.name,
                description=exercise.description,
                primary_muscle_group=exercise.primary_muscle_group,
                equipment=exercise.equipment,
                tracking_type=exercise.tracking_type,
                notes=exercise.notes,
                sync_status=exercise.sync_status,
                created_at=exercise.created_at,
                updated_at=exercise.updated_at,
                server_updated_at=exercise.server_updated_at,
                deleted_at=exercise.deleted_at,
                revision=exercise.revision,
            )
            self._session.add(row)
        else:
            if row.owner_user_id != exercise.owner_user_id:
                raise ValueError("exercise owner cannot be changed")
            row.name = exercise.name
            row.description = exercise.description
            row.primary_muscle_group = exercise.primary_muscle_group
            row.equipment = exercise.equipment
            row.tracking_type = exercise.tracking_type
            row.notes = exercise.notes
            row.sync_status = exercise.sync_status
            row.updated_at = exercise.updated_at
            row.server_updated_at = exercise.server_updated_at
            row.deleted_at = exercise.deleted_at
            row.revision = exercise.revision
        await self._session.flush()
        return exercise_from_row(row)

    async def soft_delete(self, user_id: UUID, exercise_id: UUID, deleted_at: datetime) -> bool:
        row = await self._session.get(ExerciseRow, exercise_id)
        if row is None or row.owner_user_id != user_id or row.deleted_at is not None:
            return False
        row.deleted_at = deleted_at
        row.updated_at = deleted_at
        await self._session.flush()
        return True

    async def record_change(self, exercise: Exercise, changed_at: datetime) -> int:
        change = ExerciseChangeRow(
            owner_user_id=exercise.owner_user_id,
            exercise_id=exercise.id,
            revision=exercise.revision,
            changed_at=changed_at,
        )
        self._session.add(change)
        await self._session.flush()
        return change.sequence

    async def changes_since(
        self, user_id: UUID, cursor: int, limit: int
    ) -> Sequence[tuple[int, Exercise]]:
        rows = (
            await self._session.execute(
                select(ExerciseChangeRow, ExerciseRow)
                .join(ExerciseRow, ExerciseRow.id == ExerciseChangeRow.exercise_id)
                .where(
                    ExerciseChangeRow.owner_user_id == user_id, ExerciseChangeRow.sequence > cursor
                )
                .order_by(ExerciseChangeRow.sequence.asc())
                .limit(limit)
            )
        ).all()
        return [(change.sequence, exercise_from_row(exercise)) for change, exercise in rows]


class SqlAlchemyWorkoutRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def is_id_taken(self, workout_id: UUID) -> bool:
        return await self._session.get(WorkoutRow, workout_id) is not None

    async def list(self, user_id: UUID) -> Sequence[Workout]:
        statement = (
            select(WorkoutRow)
            .options(selectinload(WorkoutRow.exercise_links))
            .where(WorkoutRow.owner_user_id == user_id)
            .order_by(WorkoutRow.updated_at.desc())
        )
        rows = (await self._session.execute(statement)).scalars().unique()
        return [workout_from_row(row) for row in rows]

    async def get(self, user_id: UUID, workout_id: UUID) -> Workout | None:
        statement = (
            select(WorkoutRow)
            .options(selectinload(WorkoutRow.exercise_links))
            .where(WorkoutRow.id == workout_id, WorkoutRow.owner_user_id == user_id)
        )
        row = (await self._session.execute(statement)).scalar_one_or_none()
        return workout_from_row(row) if row else None

    async def upsert(self, workout: Workout) -> Workout:
        statement = (
            select(WorkoutRow)
            .options(selectinload(WorkoutRow.exercise_links))
            .where(WorkoutRow.id == workout.id)
        )
        row = (await self._session.execute(statement)).scalar_one_or_none()
        if row is None:
            row = WorkoutRow(
                id=workout.id,
                owner_user_id=workout.owner_user_id,
                title=workout.title,
                status=workout.status,
                start_time=workout.start_time,
                end_time=workout.end_time,
                notes=workout.notes,
                sync_status=workout.sync_status,
                created_at=workout.created_at,
                updated_at=workout.updated_at,
                server_updated_at=workout.server_updated_at,
                exercise_links=[],
            )
            self._session.add(row)
            await self._session.flush()
        else:
            if row.owner_user_id != workout.owner_user_id:
                raise ValueError("workout owner cannot be changed")
            row.title = workout.title
            row.status = workout.status
            row.start_time = workout.start_time
            row.end_time = workout.end_time
            row.notes = workout.notes
            row.sync_status = workout.sync_status
            row.updated_at = workout.updated_at
            row.server_updated_at = workout.server_updated_at
            row.exercise_links.clear()

        row.exercise_links = [
            WorkoutExerciseRow(
                id=link.id,
                workout_id=workout.id,
                exercise_id=link.exercise_id,
                position=link.position,
            )
            for link in workout.exercises
        ]
        await self._session.flush()
        return workout_from_row(row)


class SqlAlchemyOutboxRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def add_event(
        self,
        *,
        event_id: UUID,
        aggregate_type: str,
        aggregate_id: UUID,
        event_type: str,
        payload: dict[str, object],
        occurred_at: datetime,
    ) -> bool:
        if await self._session.get(OutboxEventRow, event_id) is not None:
            return False
        self._session.add(
            OutboxEventRow(
                event_id=event_id,
                aggregate_type=aggregate_type,
                aggregate_id=aggregate_id,
                event_type=event_type,
                payload=payload,
                occurred_at=occurred_at,
                attempts=0,
            )
        )
        await self._session.flush()
        return True


class SqlAlchemyIdempotencyRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def reserve(
        self,
        *,
        scope: str,
        key: str,
        request_hash: str,
        now: datetime,
        expires_at: datetime,
        lease_expires_at: datetime,
    ) -> tuple[str, str, int | None, dict[str, object] | None]:
        statement = (
            select(IdempotencyRecordRow)
            .where(IdempotencyRecordRow.scope == scope, IdempotencyRecordRow.key == key)
            .with_for_update()
        )
        row = (await self._session.execute(statement)).scalar_one_or_none()

        def aware(value: datetime) -> datetime:
            return value if value.tzinfo is not None else value.replace(tzinfo=UTC)

        if row is not None and aware(row.expires_at) <= now:
            await self._session.delete(row)
            await self._session.flush()
            row = None
        if row is None:
            candidate = IdempotencyRecordRow(
                id=uuid4(),
                scope=scope,
                key=key,
                request_hash=request_hash,
                state="IN_PROGRESS",
                response_status=None,
                response_body=None,
                created_at=now,
                updated_at=now,
                expires_at=expires_at,
                lease_expires_at=lease_expires_at,
            )
            try:
                async with self._session.begin_nested():
                    self._session.add(candidate)
                    await self._session.flush()
                return "RESERVED", request_hash, None, None
            except IntegrityError:
                row = (await self._session.execute(statement)).scalar_one()
        if row.state == "IN_PROGRESS" and aware(row.lease_expires_at) <= now:
            row.state = "FAILED"
            row.updated_at = now
            await self._session.flush()
        return row.state, row.request_hash, row.response_status, row.response_body

    async def complete(
        self, scope: str, key: str, status: int, body: dict[str, object], now: datetime
    ) -> None:
        row = (
            await self._session.execute(
                select(IdempotencyRecordRow)
                .where(IdempotencyRecordRow.scope == scope, IdempotencyRecordRow.key == key)
                .with_for_update()
            )
        ).scalar_one()
        row.state = "COMPLETED"
        row.response_status = status
        row.response_body = body
        row.updated_at = now
        await self._session.flush()

    async def fail(self, scope: str, key: str, now: datetime) -> None:
        row = (
            await self._session.execute(
                select(IdempotencyRecordRow)
                .where(IdempotencyRecordRow.scope == scope, IdempotencyRecordRow.key == key)
                .with_for_update()
            )
        ).scalar_one_or_none()
        if row is not None:
            row.state = "FAILED"
            row.updated_at = now
            await self._session.flush()
