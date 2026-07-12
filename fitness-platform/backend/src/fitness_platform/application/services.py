import asyncio
import hashlib
import hmac
import json
from collections.abc import Callable, Sequence
from dataclasses import asdict, replace
from datetime import timedelta
from uuid import NAMESPACE_URL, UUID, uuid5

from sqlalchemy.exc import IntegrityError

from fitness_platform.core.clock import Clock
from fitness_platform.core.errors import (
    ConflictError,
    NotFoundError,
    UnauthorizedError,
    ValidationAppError,
)
from fitness_platform.core.ids import UuidProvider
from fitness_platform.core.security import create_opaque_token, hash_token
from fitness_platform.domain.enums import (
    OnboardingStatus,
    SyncStatus,
    TrackingType,
    UnitSystem,
    WorkoutStatus,
)
from fitness_platform.domain.events import (
    EventDispatcher,
    ExerciseCreated,
    GuestProfileCreated,
    WorkoutCompleted,
    WorkoutCreated,
    WorkoutStarted,
)
from fitness_platform.domain.models import (
    CatalogExercise,
    CatalogRelease,
    Exercise,
    GuestSession,
    Profile,
    Workout,
    WorkoutExercise,
)
from fitness_platform.domain.ports import UnitOfWork
from fitness_platform.domain.sync import (
    ExerciseDeletePayload,
    ExerciseUpsertPayload,
    ProfileSyncPayload,
    SyncCommand,
    WorkoutCompletePayload,
    WorkoutStartPayload,
    WorkoutUpsertPayload,
)

UowFactory = Callable[[], UnitOfWork]


class GuestService:
    def __init__(
        self,
        *,
        uow_factory: UowFactory,
        clock: Clock,
        ids: UuidProvider,
        event_dispatcher: EventDispatcher,
        token_pepper: str,
        token_ttl_hours: int,
    ) -> None:
        self._uow_factory = uow_factory
        self._clock = clock
        self._ids = ids
        self._events = event_dispatcher
        self._token_pepper = token_pepper
        self._token_ttl_hours = token_ttl_hours
        self._installation_locks: dict[UUID, asyncio.Lock] = {}

    async def create_guest(
        self, display_name: str, installation_id: UUID, recovery_secret: str
    ) -> tuple[Profile, str, bool]:
        normalized_name = display_name.strip()
        if not normalized_name:
            raise ValidationAppError("Display name must not be empty.")
        if len(recovery_secret) < 32:
            raise ValidationAppError("Recovery secret must contain at least 32 characters.")
        process_lock = self._installation_locks.setdefault(installation_id, asyncio.Lock())
        if process_lock.locked():
            raise ConflictError("Guest creation or recovery is already in progress.")
        await process_lock.acquire()
        try:
            now = self._clock.now()
            recovery_hash = hash_token(recovery_secret, self._token_pepper)
            async with self._uow_factory() as uow:
                if not await uow.guest_sessions.try_lock_installation(installation_id):
                    raise ConflictError("Guest creation or recovery is already in progress.")
                existing_session = await uow.guest_sessions.find_by_installation(installation_id)
                if existing_session is not None:
                    if not existing_session.recovery_secret_hash or not hmac.compare_digest(
                        existing_session.recovery_secret_hash, recovery_hash
                    ):
                        raise UnauthorizedError("Guest recovery proof is invalid.")
                    profile = await uow.profiles.get(existing_session.user_id)
                    if profile is None:
                        raise UnauthorizedError("Guest recovery is unavailable.")
                    token = create_opaque_token()
                    updated = await uow.guest_sessions.update_credentials(
                        replace(
                            existing_session,
                            token_hash=hash_token(token, self._token_pepper),
                            expires_at=now + timedelta(hours=self._token_ttl_hours),
                            revoked_at=None,
                        ),
                        expected_token_hash=existing_session.token_hash,
                    )
                    if not updated:
                        raise ConflictError("Guest recovery is already in progress.")
                    await uow.commit()
                    return profile, token, True
                user_id = self._ids.new()
                token = create_opaque_token()
                session = GuestSession(
                    id=self._ids.new(),
                    user_id=user_id,
                    token_hash=hash_token(token, self._token_pepper),
                    expires_at=now + timedelta(hours=self._token_ttl_hours),
                    created_at=now,
                    installation_id=installation_id,
                    recovery_secret_hash=recovery_hash,
                )
                profile = Profile(
                    user_id=user_id,
                    display_name=normalized_name,
                    unit_system=UnitSystem.METRIC,
                    onboarding_status=OnboardingStatus.NOT_STARTED,
                    sync_status=SyncStatus.SYNCED,
                    created_at=now,
                    updated_at=now,
                )
                event = GuestProfileCreated(user_id=user_id)
                await uow.users.create_guest(user_id, now)
                await uow.guest_sessions.add(session)
                await uow.profiles.upsert(profile)
                await uow.outbox.add_event(
                    event_id=event.event_id,
                    aggregate_type="profile",
                    aggregate_id=user_id,
                    event_type=type(event).__name__,
                    payload={"user_id": str(user_id)},
                    occurred_at=event.occurred_at,
                )
                await uow.commit()
        except IntegrityError as exc:
            raise ConflictError("Guest creation is already in progress.") from exc
        finally:
            process_lock.release()
            if not process_lock.locked():
                self._installation_locks.pop(installation_id, None)
        await self._events.dispatch(event)
        return profile, token, False


class ProfileService:
    def __init__(self, *, uow_factory: UowFactory, clock: Clock) -> None:
        self._uow_factory = uow_factory
        self._clock = clock

    async def get(self, user_id: UUID) -> Profile:
        async with self._uow_factory() as uow:
            profile = await uow.profiles.get(user_id)
        if profile is None:
            raise NotFoundError("Profile not found.")
        return profile

    async def update(
        self,
        *,
        user_id: UUID,
        display_name: str,
        unit_system: UnitSystem,
        onboarding_status: OnboardingStatus,
    ) -> Profile:
        normalized_name = display_name.strip()
        if not normalized_name:
            raise ValidationAppError("Display name must not be empty.")
        async with self._uow_factory() as uow:
            existing = await uow.profiles.get(user_id)
            if existing is None:
                raise NotFoundError("Profile not found.")
            updated = replace(
                existing,
                display_name=normalized_name,
                unit_system=unit_system,
                onboarding_status=onboarding_status,
                sync_status=SyncStatus.SYNCED,
                updated_at=self._clock.now(),
            )
            result = await uow.profiles.upsert(updated)
            await uow.commit()
            return result


class ExerciseService:
    def __init__(
        self,
        *,
        uow_factory: UowFactory,
        clock: Clock,
        ids: UuidProvider,
        event_dispatcher: EventDispatcher,
    ) -> None:
        self._uow_factory = uow_factory
        self._clock = clock
        self._ids = ids
        self._events = event_dispatcher

    @staticmethod
    def _validate_name(name: str) -> str:
        normalized = name.strip()
        if not normalized:
            raise ValidationAppError("Exercise name must not be empty.")
        if len(normalized) > 120:
            raise ValidationAppError("Exercise name must contain at most 120 characters.")
        return normalized

    async def list(self, user_id: UUID) -> Sequence[Exercise]:
        async with self._uow_factory() as uow:
            return await uow.exercises.list(user_id)

    async def create(
        self,
        *,
        user_id: UUID,
        exercise_id: UUID | None,
        name: str,
        description: str,
        primary_muscle_group: str,
        equipment: str,
        tracking_type: TrackingType,
        notes: str,
    ) -> Exercise:
        async with self._uow_factory() as uow:
            result, event = await self.create_in_uow(
                uow=uow,
                user_id=user_id,
                exercise_id=exercise_id,
                name=name,
                description=description,
                primary_muscle_group=primary_muscle_group,
                equipment=equipment,
                tracking_type=tracking_type,
                notes=notes,
            )
            await uow.commit()
        await self._events.dispatch(event)
        return result

    async def create_in_uow(
        self,
        *,
        uow: UnitOfWork,
        user_id: UUID,
        exercise_id: UUID | None,
        name: str,
        description: str,
        primary_muscle_group: str,
        equipment: str,
        tracking_type: TrackingType,
        notes: str,
    ) -> tuple[Exercise, ExerciseCreated]:
        now = self._clock.now()
        exercise = Exercise(
            id=exercise_id or self._ids.new(),
            owner_user_id=user_id,
            name=self._validate_name(name),
            description=description.strip(),
            primary_muscle_group=primary_muscle_group.strip() or "Unspecified",
            equipment=equipment.strip() or "None",
            tracking_type=tracking_type,
            notes=notes.strip(),
            sync_status=SyncStatus.SYNCED,
            created_at=now,
            updated_at=now,
            server_updated_at=now,
            revision=1,
        )
        event = ExerciseCreated(exercise_id=exercise.id, user_id=user_id)
        if await uow.exercises.is_id_taken(exercise.id):
            raise ConflictError("Exercise identifier is unavailable.")
        existing = await uow.exercises.get(user_id, exercise.id)
        if existing is not None:
            raise ConflictError("Exercise already exists.")
        result = await uow.exercises.upsert(exercise)
        await uow.exercises.record_change(result, now)
        await uow.outbox.add_event(
            event_id=event.event_id,
            aggregate_type="exercise",
            aggregate_id=exercise.id,
            event_type=type(event).__name__,
            payload={"exercise_id": str(exercise.id), "user_id": str(user_id)},
            occurred_at=event.occurred_at,
        )
        return result, event

    async def update(
        self,
        *,
        user_id: UUID,
        exercise_id: UUID,
        name: str,
        description: str,
        primary_muscle_group: str,
        equipment: str,
        tracking_type: TrackingType,
        notes: str,
    ) -> Exercise:
        now = self._clock.now()
        async with self._uow_factory() as uow:
            existing = await uow.exercises.get(user_id, exercise_id)
            if existing is None:
                raise NotFoundError("Exercise not found.")
            updated = replace(
                existing,
                name=self._validate_name(name),
                description=description.strip(),
                primary_muscle_group=primary_muscle_group.strip() or "Unspecified",
                equipment=equipment.strip() or "None",
                tracking_type=tracking_type,
                notes=notes.strip(),
                sync_status=SyncStatus.SYNCED,
                updated_at=now,
                server_updated_at=now,
                revision=existing.revision + 1,
            )
            result = await uow.exercises.upsert(updated)
            await uow.exercises.record_change(result, now)
            await uow.commit()
            return result

    async def delete(self, *, user_id: UUID, exercise_id: UUID) -> None:
        now = self._clock.now()
        async with self._uow_factory() as uow:
            existing = await uow.exercises.get(user_id, exercise_id)
            if existing is None:
                raise NotFoundError("Exercise not found.")
            deleted = await uow.exercises.upsert(
                replace(
                    existing,
                    deleted_at=now,
                    updated_at=now,
                    server_updated_at=now,
                    revision=existing.revision + 1,
                )
            )
            await uow.exercises.record_change(deleted, now)
            await uow.commit()


class WorkoutService:
    def __init__(
        self,
        *,
        uow_factory: UowFactory,
        clock: Clock,
        ids: UuidProvider,
        event_dispatcher: EventDispatcher,
    ) -> None:
        self._uow_factory = uow_factory
        self._clock = clock
        self._ids = ids
        self._events = event_dispatcher

    @staticmethod
    async def _validate_exercise_ids(
        uow: UnitOfWork, user_id: UUID, exercise_ids: Sequence[UUID]
    ) -> None:
        if len(exercise_ids) > 50:
            raise ValidationAppError("A workout may contain at most 50 exercises.")
        if len(set(exercise_ids)) != len(exercise_ids):
            raise ValidationAppError("Workout exercise identifiers must be unique.")
        if await uow.exercises.owned_active_ids(user_id, exercise_ids) != set(exercise_ids):
            raise ValidationAppError("A referenced private exercise is unavailable.")

    async def list(self, user_id: UUID) -> Sequence[Workout]:
        async with self._uow_factory() as uow:
            return await uow.workouts.list(user_id)

    async def create(
        self,
        *,
        user_id: UUID,
        workout_id: UUID | None,
        title: str,
        notes: str,
        exercise_ids: Sequence[UUID],
    ) -> Workout:
        async with self._uow_factory() as uow:
            result, event = await self.create_in_uow(
                uow=uow,
                user_id=user_id,
                workout_id=workout_id,
                title=title,
                notes=notes,
                exercise_ids=exercise_ids,
            )
            await uow.commit()
        await self._events.dispatch(event)
        return result

    async def create_in_uow(
        self,
        *,
        uow: UnitOfWork,
        user_id: UUID,
        workout_id: UUID | None,
        title: str,
        notes: str,
        exercise_ids: Sequence[UUID],
    ) -> tuple[Workout, WorkoutCreated]:
        normalized_title = title.strip()
        if not normalized_title:
            raise ValidationAppError("Workout title must not be empty.")
        now = self._clock.now()
        new_id = workout_id or self._ids.new()
        links = [
            WorkoutExercise(
                id=self._ids.new(),
                workout_id=new_id,
                exercise_id=exercise_id,
                position=position,
            )
            for position, exercise_id in enumerate(exercise_ids)
        ]
        workout = Workout(
            id=new_id,
            owner_user_id=user_id,
            title=normalized_title,
            status=WorkoutStatus.PLANNED,
            notes=notes.strip(),
            exercises=links,
            sync_status=SyncStatus.SYNCED,
            created_at=now,
            updated_at=now,
            server_updated_at=now,
        )
        event = WorkoutCreated(workout_id=new_id, user_id=user_id)
        if await uow.workouts.is_id_taken(new_id):
            raise ConflictError("Workout identifier is unavailable.")
        existing = await uow.workouts.get(user_id, new_id)
        if existing is not None:
            raise ConflictError("Workout already exists.")
        await self._validate_exercise_ids(uow, user_id, exercise_ids)
        result = await uow.workouts.upsert(workout)
        await uow.outbox.add_event(
            event_id=event.event_id,
            aggregate_type="workout",
            aggregate_id=new_id,
            event_type=type(event).__name__,
            payload={"workout_id": str(new_id), "user_id": str(user_id)},
            occurred_at=event.occurred_at,
        )
        return result, event

    async def update(
        self,
        *,
        user_id: UUID,
        workout_id: UUID,
        title: str,
        notes: str,
        exercise_ids: Sequence[UUID],
    ) -> Workout:
        async with self._uow_factory() as uow:
            result = await self.update_in_uow(
                uow=uow,
                user_id=user_id,
                workout_id=workout_id,
                title=title,
                notes=notes,
                exercise_ids=exercise_ids,
            )
            await uow.commit()
            return result

    async def update_in_uow(
        self,
        *,
        uow: UnitOfWork,
        user_id: UUID,
        workout_id: UUID,
        title: str,
        notes: str,
        exercise_ids: Sequence[UUID],
    ) -> Workout:
        normalized_title = title.strip()
        if not normalized_title:
            raise ValidationAppError("Workout title must not be empty.")
        existing = await uow.workouts.get(user_id, workout_id)
        if existing is None:
            raise NotFoundError("Workout not found.")
        if existing.status == WorkoutStatus.COMPLETED:
            raise ConflictError("Completed workouts cannot be edited.")
        await self._validate_exercise_ids(uow, user_id, exercise_ids)
        links = [
            WorkoutExercise(
                id=self._ids.new(),
                workout_id=workout_id,
                exercise_id=exercise_id,
                position=position,
            )
            for position, exercise_id in enumerate(exercise_ids)
        ]
        now = self._clock.now()
        updated = replace(
            existing,
            title=normalized_title,
            notes=notes.strip(),
            exercises=links,
            sync_status=SyncStatus.SYNCED,
            updated_at=now,
            server_updated_at=now,
        )
        return await uow.workouts.upsert(updated)

    async def start(self, *, user_id: UUID, workout_id: UUID) -> Workout:
        async with self._uow_factory() as uow:
            result, event = await self.start_in_uow(uow=uow, user_id=user_id, workout_id=workout_id)
            await uow.commit()
        if event is not None:
            await self._events.dispatch(event)
        return result

    async def start_in_uow(
        self, *, uow: UnitOfWork, user_id: UUID, workout_id: UUID
    ) -> tuple[Workout, WorkoutStarted | None]:
        existing = await uow.workouts.get(user_id, workout_id)
        if existing is None:
            raise NotFoundError("Workout not found.")
        if existing.status == WorkoutStatus.COMPLETED:
            raise ConflictError("Completed workout cannot be started again.")
        if existing.status == WorkoutStatus.IN_PROGRESS:
            return existing, None
        now = self._clock.now()
        result = await uow.workouts.upsert(
            replace(
                existing,
                status=WorkoutStatus.IN_PROGRESS,
                start_time=existing.start_time or now,
                updated_at=now,
                server_updated_at=now,
            )
        )
        event = WorkoutStarted(workout_id=workout_id, user_id=user_id)
        await uow.outbox.add_event(
            event_id=event.event_id,
            aggregate_type="workout",
            aggregate_id=workout_id,
            event_type=type(event).__name__,
            payload={"workout_id": str(workout_id), "user_id": str(user_id)},
            occurred_at=event.occurred_at,
        )
        return result, event

    async def complete(self, *, user_id: UUID, workout_id: UUID) -> tuple[Workout, bool]:
        async with self._uow_factory() as uow:
            result, inserted, event = await self.complete_in_uow(
                uow=uow, user_id=user_id, workout_id=workout_id
            )
            await uow.commit()
        if inserted and event is not None:
            await self._events.dispatch(event)
        return result, inserted

    async def complete_in_uow(
        self, *, uow: UnitOfWork, user_id: UUID, workout_id: UUID
    ) -> tuple[Workout, bool, WorkoutCompleted | None]:
        existing = await uow.workouts.get(user_id, workout_id)
        if existing is None:
            raise NotFoundError("Workout not found.")
        if existing.status == WorkoutStatus.COMPLETED:
            return existing, False, None
        if existing.status != WorkoutStatus.IN_PROGRESS:
            raise ConflictError("Only an in-progress workout can be completed.")
        now = self._clock.now()
        result = await uow.workouts.upsert(
            replace(
                existing,
                status=WorkoutStatus.COMPLETED,
                start_time=existing.start_time,
                end_time=now,
                updated_at=now,
                server_updated_at=now,
            )
        )
        event_id = uuid5(NAMESPACE_URL, f"workout-completed:{user_id}:{workout_id}")
        event = WorkoutCompleted(event_id=event_id, workout_id=workout_id, user_id=user_id)
        inserted = await uow.outbox.add_event(
            event_id=event.event_id,
            aggregate_type="workout",
            aggregate_id=workout_id,
            event_type=type(event).__name__,
            payload={"workout_id": str(workout_id), "user_id": str(user_id)},
            occurred_at=event.occurred_at,
        )
        return result, inserted, event


class CatalogService:
    def __init__(self, *, uow_factory: UowFactory) -> None:
        self._uow_factory = uow_factory

    async def list(
        self,
        muscle: str | None,
        equipment: str | None,
        query: str | None = None,
        limit: int | None = 100,
        offset: int = 0,
    ) -> Sequence[CatalogExercise]:
        async with self._uow_factory() as uow:
            return await uow.catalog.list(muscle, equipment, query, limit, offset)

    async def count(self, muscle: str | None, equipment: str | None, query: str | None) -> int:
        async with self._uow_factory() as uow:
            return await uow.catalog.count(muscle, equipment, query)

    async def release(self) -> CatalogRelease | None:
        async with self._uow_factory() as uow:
            return await uow.catalog.get_release()

    async def get(self, exercise_id: UUID) -> CatalogExercise:
        async with self._uow_factory() as uow:
            exercise = await uow.catalog.get(exercise_id)
        if exercise is None:
            raise NotFoundError("Catalog exercise not found.")
        return exercise

    async def muscles(self) -> Sequence[tuple[str, str]]:
        async with self._uow_factory() as uow:
            return await uow.catalog.list_muscles()

    async def equipment(self) -> Sequence[tuple[str, str]]:
        async with self._uow_factory() as uow:
            return await uow.catalog.list_equipment()


class SyncService:
    """Cursor-based private-exercise synchronization with optimistic revisions."""

    def __init__(
        self,
        *,
        uow_factory: UowFactory,
        clock: Clock,
        ids: UuidProvider,
        workouts: WorkoutService,
    ) -> None:
        self._uow_factory = uow_factory
        self._clock = clock
        self._ids = ids
        self._workouts = workouts

    async def cleanup_processed_operations(self, batch_limit: int = 500) -> int:
        if not 1 <= batch_limit <= 10_000:
            raise ValueError("batch_limit must be between 1 and 10000")
        async with self._uow_factory() as uow:
            deleted = await uow.processed_sync_operations.delete_expired(
                self._clock.now(), batch_limit
            )
            await uow.commit()
        return deleted

    async def push(
        self, *, user_id: UUID, operations: list[SyncCommand]
    ) -> list[dict[str, object]]:
        async with self._uow_factory() as uow:
            results = await self.push_in_uow(uow=uow, user_id=user_id, operations=operations)
            await uow.commit()
        return results

    async def push_in_uow(
        self,
        *,
        uow: UnitOfWork,
        user_id: UUID,
        operations: list[SyncCommand],
    ) -> list[dict[str, object]]:
        results: list[dict[str, object]] = []
        now = self._clock.now()
        for operation in operations:
            operation_id = operation.operation_id
            payload = operation.payload
            request_hash = hashlib.sha256(
                json.dumps(asdict(payload), default=str, sort_keys=True).encode()
            ).hexdigest()
            reserved, existing_hash, replay = await uow.processed_sync_operations.reserve(
                user_id,
                operation_id,
                request_hash,
                now,
                now + timedelta(days=7),
            )
            if existing_hash != request_hash:
                raise ConflictError("Sync operation ID was reused with a different payload.")
            if not reserved:
                if replay is None:
                    raise ConflictError("Sync operation is already in progress.")
                results.append(replay)
                continue
            exercise: Exercise | None = None
            if isinstance(payload, ProfileSyncPayload):
                existing_profile = await uow.profiles.get(user_id)
                created_at = existing_profile.created_at if existing_profile else now
                profile = Profile(
                    user_id=user_id,
                    display_name=payload.display_name,
                    unit_system=payload.unit_system,
                    onboarding_status=payload.onboarding_status,
                    sync_status=SyncStatus.SYNCED,
                    created_at=created_at,
                    updated_at=now,
                )
                await uow.profiles.upsert(profile)
                aggregate_id = user_id
            elif isinstance(payload, (ExerciseUpsertPayload, ExerciseDeletePayload)):
                exercise_id = payload.id
                aggregate_id = exercise_id
                existing_exercise = await uow.exercises.get_including_deleted(user_id, exercise_id)
                if existing_exercise is None and await uow.exercises.is_id_taken(exercise_id):
                    raise ConflictError("Exercise identifier is unavailable.")
                base_revision = payload.base_revision
                if existing_exercise is not None and base_revision != existing_exercise.revision:
                    conflict_result = {
                        "operation_id": str(operation_id),
                        "aggregate_id": str(exercise_id),
                        "status": "CONFLICT",
                        "server_updated_at": (
                            existing_exercise.server_updated_at or existing_exercise.updated_at
                        ).isoformat(),
                        "revision": existing_exercise.revision,
                        "remote_exercise": asdict(existing_exercise),
                    }
                    serialized_conflict = json.loads(json.dumps(conflict_result, default=str))
                    await uow.processed_sync_operations.complete(
                        user_id, operation_id, serialized_conflict
                    )
                    results.append(serialized_conflict)
                    continue
                if isinstance(payload, ExerciseDeletePayload):
                    if existing_exercise is None:
                        raise ValidationAppError("Cannot delete a missing exercise.")
                    exercise = Exercise(
                        **{**existing_exercise.__dict__}
                        if hasattr(existing_exercise, "__dict__")
                        else {
                            "id": existing_exercise.id,
                            "owner_user_id": existing_exercise.owner_user_id,
                            "name": existing_exercise.name,
                            "description": existing_exercise.description,
                            "primary_muscle_group": existing_exercise.primary_muscle_group,
                            "equipment": existing_exercise.equipment,
                            "tracking_type": existing_exercise.tracking_type,
                            "notes": existing_exercise.notes,
                            "sync_status": SyncStatus.SYNCED,
                            "created_at": existing_exercise.created_at,
                            "updated_at": now,
                            "server_updated_at": now,
                            "deleted_at": now,
                            "revision": existing_exercise.revision + 1,
                        }
                    )
                    exercise = await uow.exercises.upsert(exercise)
                else:
                    exercise = Exercise(
                        id=exercise_id,
                        owner_user_id=user_id,
                        name=ExerciseService._validate_name(payload.name),
                        description=payload.description,
                        primary_muscle_group=payload.primary_muscle_group,
                        equipment=payload.equipment,
                        tracking_type=payload.tracking_type,
                        notes=payload.notes,
                        sync_status=SyncStatus.SYNCED,
                        created_at=existing_exercise.created_at if existing_exercise else now,
                        updated_at=now,
                        server_updated_at=now,
                        deleted_at=None,
                        revision=(existing_exercise.revision + 1) if existing_exercise else 1,
                    )
                    exercise = await uow.exercises.upsert(exercise)
                await uow.exercises.record_change(exercise, now)
            elif isinstance(payload, WorkoutUpsertPayload):
                workout_id = payload.id
                aggregate_id = workout_id
                existing_workout = await uow.workouts.get(user_id, workout_id)
                if existing_workout is None:
                    await self._workouts.create_in_uow(
                        uow=uow,
                        user_id=user_id,
                        workout_id=workout_id,
                        title=payload.title,
                        notes=payload.notes,
                        exercise_ids=payload.exercise_ids,
                    )
                else:
                    await self._workouts.update_in_uow(
                        uow=uow,
                        user_id=user_id,
                        workout_id=workout_id,
                        title=payload.title,
                        notes=payload.notes,
                        exercise_ids=payload.exercise_ids,
                    )
            elif isinstance(payload, WorkoutStartPayload):
                aggregate_id = payload.id
                await self._workouts.start_in_uow(uow=uow, user_id=user_id, workout_id=payload.id)
            elif isinstance(payload, WorkoutCompletePayload):
                aggregate_id = payload.id
                await self._workouts.complete_in_uow(
                    uow=uow, user_id=user_id, workout_id=payload.id
                )
            result: dict[str, object] = {
                "operation_id": str(operation_id),
                "aggregate_id": str(aggregate_id),
                "status": "SYNCED",
                "server_updated_at": now.isoformat(),
                "revision": exercise.revision if exercise is not None else None,
            }
            await uow.processed_sync_operations.complete(user_id, operation_id, result)
            results.append(result)
        return results

    async def pull(
        self, *, user_id: UUID, cursor: int, limit: int
    ) -> tuple[list[dict[str, object]], int, bool]:
        async with self._uow_factory() as uow:
            changes = await uow.exercises.changes_since(user_id, cursor, limit + 1)
        has_more = len(changes) > limit
        page = changes[:limit]
        next_cursor = page[-1][0] if page else cursor
        return (
            [
                {
                    "cursor": sequence,
                    "exercise": exercise,
                    "deleted": exercise.deleted_at is not None,
                }
                for sequence, exercise in page
            ],
            next_cursor,
            has_more,
        )
