from collections.abc import Sequence
from datetime import datetime
from uuid import UUID, uuid4

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from fitness_platform.domain.enums import UserKind
from fitness_platform.domain.models import Exercise, GuestSession, Profile, Workout, WorkoutExercise
from fitness_platform.infrastructure.orm import (
    ExerciseRow,
    GuestSessionRow,
    IdempotencyRecordRow,
    OutboxEventRow,
    ProfileRow,
    UserRow,
    WorkoutExerciseRow,
    WorkoutRow,
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


class SqlAlchemyWorkoutRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

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

    async def get(self, scope: str, key: str) -> tuple[str, int, dict[str, object]] | None:
        statement = select(IdempotencyRecordRow).where(
            IdempotencyRecordRow.scope == scope,
            IdempotencyRecordRow.key == key,
        )
        row = (await self._session.execute(statement)).scalar_one_or_none()
        if row is None:
            return None
        return row.request_hash, row.response_status, row.response_body

    async def add(
        self,
        *,
        scope: str,
        key: str,
        request_hash: str,
        response_status: int,
        response_body: dict[str, object],
        created_at: datetime,
        expires_at: datetime,
    ) -> None:
        self._session.add(
            IdempotencyRecordRow(
                id=uuid4(),
                scope=scope,
                key=key,
                request_hash=request_hash,
                response_status=response_status,
                response_body=response_body,
                created_at=created_at,
                expires_at=expires_at,
            )
        )
        await self._session.flush()
