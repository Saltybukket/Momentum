from collections.abc import Sequence
from datetime import UTC, datetime, timedelta
from uuid import UUID, uuid4

from sqlalchemy import delete, select, text, update
from sqlalchemy.dialects.postgresql import insert as postgresql_insert
from sqlalchemy.dialects.sqlite import insert as sqlite_insert
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from fitness_platform.domain.enums import UserKind
from fitness_platform.domain.models import (
    Exercise,
    GuestSession,
    OutboxRecord,
    Profile,
    Workout,
    WorkoutExercise,
)
from fitness_platform.infrastructure.orm import (
    ExerciseChangeRow,
    ExerciseRow,
    GuestSessionRow,
    IdempotencyRecordRow,
    OutboxEventRow,
    ProcessedSyncOperationRow,
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

    async def try_lock_installation(self, installation_id: UUID) -> bool:
        bind = self._session.get_bind()
        if bind.dialect.name != "postgresql":
            return True
        lock_key = installation_id.int & ((1 << 63) - 1)
        acquired = await self._session.scalar(
            text("SELECT pg_try_advisory_xact_lock(:lock_key)"), {"lock_key": lock_key}
        )
        return bool(acquired)

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

    async def update_credentials(self, session: GuestSession, expected_token_hash: str) -> bool:
        result = await self._session.execute(
            update(GuestSessionRow)
            .where(
                GuestSessionRow.id == session.id,
                GuestSessionRow.token_hash == expected_token_hash,
            )
            .values(
                token_hash=session.token_hash,
                expires_at=session.expires_at,
                revoked_at=session.revoked_at,
                recovery_secret_hash=session.recovery_secret_hash,
            )
            .returning(GuestSessionRow.id)
        )
        return result.scalar_one_or_none() is not None

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

    async def owned_active_ids(self, user_id: UUID, exercise_ids: Sequence[UUID]) -> set[UUID]:
        if not exercise_ids:
            return set()
        rows = (
            (
                await self._session.execute(
                    select(ExerciseRow.id).where(
                        ExerciseRow.owner_user_id == user_id,
                        ExerciseRow.id.in_(exercise_ids),
                        ExerciseRow.deleted_at.is_(None),
                    )
                )
            )
            .scalars()
            .all()
        )
        return set(rows)

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
            await self._session.flush()

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
                status="PENDING",
                next_attempt_at=occurred_at,
                max_attempts=5,
            )
        )
        await self._session.flush()
        return True

    async def claim_due(
        self,
        *,
        worker_id: str,
        claim_token: UUID,
        now: datetime,
        lease_expires_at: datetime,
        limit: int,
    ) -> Sequence[OutboxRecord]:
        due = (
            (OutboxEventRow.status.in_(["PENDING", "FAILED"]))
            & (OutboxEventRow.next_attempt_at <= now)
        ) | ((OutboxEventRow.status == "PROCESSING") & (OutboxEventRow.lease_expires_at <= now))
        statement = (
            select(OutboxEventRow)
            .where(due)
            .order_by(
                OutboxEventRow.next_attempt_at, OutboxEventRow.occurred_at, OutboxEventRow.event_id
            )
            .limit(limit)
        )
        if self._session.get_bind().dialect.name == "postgresql":
            statement = statement.with_for_update(skip_locked=True)
        rows = (await self._session.execute(statement)).scalars().all()
        for row in rows:
            row.status = "PROCESSING"
            row.claim_owner = worker_id
            row.claim_token = claim_token
            row.lease_expires_at = lease_expires_at
        await self._session.flush()
        return [self._to_record(row) for row in rows]

    async def extend_lease(
        self,
        event_id: UUID,
        worker_id: str,
        claim_token: UUID,
        lease_expires_at: datetime,
    ) -> bool:
        result = await self._session.execute(
            update(OutboxEventRow)
            .where(
                OutboxEventRow.event_id == event_id,
                OutboxEventRow.status == "PROCESSING",
                OutboxEventRow.claim_owner == worker_id,
                OutboxEventRow.claim_token == claim_token,
            )
            .values(lease_expires_at=lease_expires_at)
            .returning(OutboxEventRow.event_id)
        )
        return result.scalar_one_or_none() is not None

    async def mark_processed(
        self, event_id: UUID, worker_id: str, claim_token: UUID, now: datetime
    ) -> bool:
        row = await self._session.get(OutboxEventRow, event_id)
        if (
            row is None
            or row.status != "PROCESSING"
            or row.claim_owner != worker_id
            or row.claim_token != claim_token
        ):
            return False
        row.status = "PROCESSED"
        row.processed_at = now
        row.claim_owner = None
        row.claim_token = None
        row.lease_expires_at = None
        row.last_error = None
        await self._session.flush()
        return True

    async def mark_failed(
        self,
        event_id: UUID,
        worker_id: str,
        claim_token: UUID,
        now: datetime,
        error: str,
    ) -> str:
        row = await self._session.get(OutboxEventRow, event_id)
        if (
            row is None
            or row.status != "PROCESSING"
            or row.claim_owner != worker_id
            or row.claim_token != claim_token
        ):
            return "LOST_CLAIM"
        row.attempts += 1
        row.last_error = error[:1000]
        row.claim_owner = None
        row.claim_token = None
        row.lease_expires_at = None
        if row.attempts >= row.max_attempts:
            row.status = "DEAD_LETTER"
        else:
            row.status = "FAILED"
            row.next_attempt_at = now + timedelta(seconds=min(3600, 2**row.attempts))
        await self._session.flush()
        return row.status

    @staticmethod
    def _to_record(row: OutboxEventRow) -> OutboxRecord:
        return OutboxRecord(
            event_id=row.event_id,
            aggregate_type=row.aggregate_type,
            aggregate_id=row.aggregate_id,
            event_type=row.event_type,
            payload=row.payload,
            occurred_at=row.occurred_at,
            processed_at=row.processed_at,
            attempts=row.attempts,
            last_error=row.last_error,
            status=row.status,
            claim_owner=row.claim_owner,
            claim_token=row.claim_token,
            lease_expires_at=row.lease_expires_at,
            next_attempt_at=row.next_attempt_at,
            max_attempts=row.max_attempts,
        )


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
            values = dict(
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
            dialect = self._session.get_bind().dialect.name
            if dialect in {"postgresql", "sqlite"}:
                insert = postgresql_insert if dialect == "postgresql" else sqlite_insert
                inserted_id = await self._session.scalar(
                    insert(IdempotencyRecordRow)
                    .values(**values)
                    .on_conflict_do_nothing(index_elements=["scope", "key"])
                    .returning(IdempotencyRecordRow.id)
                )
                if inserted_id is not None:
                    return "RESERVED", request_hash, None, None
                row = (await self._session.execute(statement)).scalar_one()
            else:
                candidate = IdempotencyRecordRow(**values)
                self._session.add(candidate)
                await self._session.flush()
                return "RESERVED", request_hash, None, None
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

    async def delete_expired(self, now: datetime, limit: int) -> int:
        ids = (
            (
                await self._session.execute(
                    select(IdempotencyRecordRow.id)
                    .where(IdempotencyRecordRow.expires_at <= now)
                    .order_by(IdempotencyRecordRow.expires_at, IdempotencyRecordRow.id)
                    .limit(limit)
                )
            )
            .scalars()
            .all()
        )
        if not ids:
            return 0
        await self._session.execute(
            delete(IdempotencyRecordRow).where(IdempotencyRecordRow.id.in_(ids))
        )
        return len(ids)


class SqlAlchemyProcessedSyncOperationRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def reserve(
        self,
        owner_user_id: UUID,
        operation_id: UUID,
        request_hash: str,
        now: datetime,
        expires_at: datetime,
    ) -> tuple[bool, str, dict[str, object] | None]:
        values = {
            "owner_user_id": owner_user_id,
            "operation_id": operation_id,
            "request_hash": request_hash,
            "result": None,
            "created_at": now,
            "expires_at": expires_at,
        }
        dialect = self._session.get_bind().dialect.name
        insert = postgresql_insert if dialect == "postgresql" else sqlite_insert
        inserted = await self._session.scalar(
            insert(ProcessedSyncOperationRow)
            .values(**values)
            .on_conflict_do_nothing(index_elements=["owner_user_id", "operation_id"])
            .returning(ProcessedSyncOperationRow.operation_id)
        )
        if inserted is not None:
            return True, request_hash, None
        row = (
            await self._session.execute(
                select(ProcessedSyncOperationRow)
                .where(
                    ProcessedSyncOperationRow.owner_user_id == owner_user_id,
                    ProcessedSyncOperationRow.operation_id == operation_id,
                )
                .with_for_update()
            )
        ).scalar_one()
        expires_at_value = (
            row.expires_at
            if row.expires_at.tzinfo is not None
            else row.expires_at.replace(tzinfo=UTC)
        )
        if expires_at_value <= now:
            row.request_hash = request_hash
            row.result = None
            row.created_at = now
            row.expires_at = expires_at
            await self._session.flush()
            return True, request_hash, None
        return False, row.request_hash, row.result

    async def complete(
        self, owner_user_id: UUID, operation_id: UUID, result: dict[str, object]
    ) -> None:
        row = await self._session.get(ProcessedSyncOperationRow, (owner_user_id, operation_id))
        if row is None:
            raise RuntimeError("Processed sync operation reservation disappeared.")
        row.result = result
        await self._session.flush()

    async def delete_expired(self, now: datetime, limit: int) -> int:
        ids = (
            await self._session.execute(
                select(
                    ProcessedSyncOperationRow.owner_user_id,
                    ProcessedSyncOperationRow.operation_id,
                )
                .where(ProcessedSyncOperationRow.expires_at <= now)
                .order_by(ProcessedSyncOperationRow.expires_at)
                .limit(limit)
            )
        ).all()
        for owner_user_id, operation_id in ids:
            await self._session.execute(
                delete(ProcessedSyncOperationRow).where(
                    ProcessedSyncOperationRow.owner_user_id == owner_user_id,
                    ProcessedSyncOperationRow.operation_id == operation_id,
                )
            )
        return len(ids)
