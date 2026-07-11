from collections.abc import Callable, Sequence
from dataclasses import asdict, replace
from datetime import timedelta
from uuid import NAMESPACE_URL, UUID, uuid5

from fitness_platform.core.clock import Clock
from fitness_platform.core.errors import ConflictError, NotFoundError, ValidationAppError
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
from fitness_platform.domain.models import Exercise, GuestSession, Profile, Workout, WorkoutExercise
from fitness_platform.domain.ports import UnitOfWork

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

    async def create_guest(self, display_name: str) -> tuple[Profile, str]:
        normalized_name = display_name.strip()
        if not normalized_name:
            raise ValidationAppError("Display name must not be empty.")
        now = self._clock.now()
        user_id = self._ids.new()
        token = create_opaque_token()
        session = GuestSession(
            id=self._ids.new(),
            user_id=user_id,
            token_hash=hash_token(token, self._token_pepper),
            expires_at=now + timedelta(hours=self._token_ttl_hours),
            created_at=now,
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
        async with self._uow_factory() as uow:
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
        await self._events.dispatch(event)
        return profile, token


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
        async with self._uow_factory() as uow:
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
            await uow.commit()
        await self._events.dispatch(event)
        return result

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
        async with self._uow_factory() as uow:
            existing = await uow.workouts.get(user_id, new_id)
            if existing is not None:
                raise ConflictError("Workout already exists.")
            for exercise_id in exercise_ids:
                if await uow.exercises.get(user_id, exercise_id) is None:
                    raise ValidationAppError(f"Exercise {exercise_id} does not exist.")
            result = await uow.workouts.upsert(workout)
            await uow.outbox.add_event(
                event_id=event.event_id,
                aggregate_type="workout",
                aggregate_id=new_id,
                event_type=type(event).__name__,
                payload={"workout_id": str(new_id), "user_id": str(user_id)},
                occurred_at=event.occurred_at,
            )
            await uow.commit()
        await self._events.dispatch(event)
        return result

    async def update(
        self,
        *,
        user_id: UUID,
        workout_id: UUID,
        title: str,
        notes: str,
        exercise_ids: Sequence[UUID],
        status: WorkoutStatus | None = None,
    ) -> Workout:
        normalized_title = title.strip()
        if not normalized_title:
            raise ValidationAppError("Workout title must not be empty.")
        async with self._uow_factory() as uow:
            existing = await uow.workouts.get(user_id, workout_id)
            if existing is None:
                raise NotFoundError("Workout not found.")
            for exercise_id in exercise_ids:
                if await uow.exercises.get(user_id, exercise_id) is None:
                    raise ValidationAppError(f"Exercise {exercise_id} does not exist.")
            links = [
                WorkoutExercise(
                    id=self._ids.new(),
                    workout_id=workout_id,
                    exercise_id=exercise_id,
                    position=position,
                )
                for position, exercise_id in enumerate(exercise_ids)
            ]
            updated = replace(
                existing,
                title=normalized_title,
                notes=notes.strip(),
                status=status or existing.status,
                exercises=links,
                sync_status=SyncStatus.SYNCED,
                updated_at=self._clock.now(),
                server_updated_at=self._clock.now(),
            )
            result = await uow.workouts.upsert(updated)
            await uow.commit()
            return result

    async def start(self, *, user_id: UUID, workout_id: UUID) -> Workout:
        async with self._uow_factory() as uow:
            existing = await uow.workouts.get(user_id, workout_id)
            if existing is None:
                raise NotFoundError("Workout not found.")
            if existing.status == WorkoutStatus.COMPLETED:
                raise ConflictError("Completed workout cannot be started again.")
            if existing.status == WorkoutStatus.IN_PROGRESS:
                return existing
            now = self._clock.now()
            updated = replace(
                existing,
                status=WorkoutStatus.IN_PROGRESS,
                start_time=existing.start_time or now,
                updated_at=now,
                server_updated_at=now,
            )
            result = await uow.workouts.upsert(updated)
            event = WorkoutStarted(workout_id=workout_id, user_id=user_id)
            await uow.outbox.add_event(
                event_id=event.event_id,
                aggregate_type="workout",
                aggregate_id=workout_id,
                event_type=type(event).__name__,
                payload={"workout_id": str(workout_id), "user_id": str(user_id)},
                occurred_at=event.occurred_at,
            )
            await uow.commit()
        await self._events.dispatch(event)
        return result

    async def complete(self, *, user_id: UUID, workout_id: UUID) -> tuple[Workout, bool]:
        async with self._uow_factory() as uow:
            existing = await uow.workouts.get(user_id, workout_id)
            if existing is None:
                raise NotFoundError("Workout not found.")
            if existing.status == WorkoutStatus.COMPLETED:
                return existing, False
            now = self._clock.now()
            updated = replace(
                existing,
                status=WorkoutStatus.COMPLETED,
                start_time=existing.start_time or now,
                end_time=now,
                updated_at=now,
                server_updated_at=now,
            )
            result = await uow.workouts.upsert(updated)
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
            await uow.commit()
        if inserted:
            await self._events.dispatch(event)
        return result, inserted


class SyncService:
    """Cursor-based private-exercise synchronization with optimistic revisions."""

    def __init__(self, *, uow_factory: UowFactory, clock: Clock, ids: UuidProvider) -> None:
        self._uow_factory = uow_factory
        self._clock = clock
        self._ids = ids

    async def push(
        self, *, user_id: UUID, operations: list[dict[str, object]]
    ) -> list[dict[str, object]]:
        results: list[dict[str, object]] = []
        now = self._clock.now()
        async with self._uow_factory() as uow:
            for operation in operations:
                operation_id = UUID(str(operation["operation_id"]))
                entity_type = str(operation["entity_type"])
                action = str(operation["action"])
                payload_value = operation.get("payload")
                if not isinstance(payload_value, dict):
                    raise ValidationAppError("Sync operation payload must be an object.")
                payload: dict[str, object] = {
                    str(key): value for key, value in payload_value.items()
                }
                if entity_type == "profile" and action == "UPSERT":
                    existing_profile = await uow.profiles.get(user_id)
                    created_at = existing_profile.created_at if existing_profile else now
                    profile = Profile(
                        user_id=user_id,
                        display_name=str(payload.get("display_name", "Guest")).strip() or "Guest",
                        unit_system=UnitSystem(str(payload.get("unit_system", UnitSystem.METRIC))),
                        onboarding_status=OnboardingStatus(
                            str(payload.get("onboarding_status", OnboardingStatus.NOT_STARTED))
                        ),
                        sync_status=SyncStatus.SYNCED,
                        created_at=created_at,
                        updated_at=now,
                    )
                    await uow.profiles.upsert(profile)
                    aggregate_id = user_id
                elif entity_type == "exercise":
                    exercise_id = UUID(str(payload["id"]))
                    aggregate_id = exercise_id
                    existing_exercise = await uow.exercises.get_including_deleted(
                        user_id, exercise_id
                    )
                    base_revision = payload.get("base_revision")
                    if (
                        existing_exercise is not None
                        and base_revision != existing_exercise.revision
                    ):
                        results.append(
                            {
                                "operation_id": str(operation_id),
                                "aggregate_id": str(exercise_id),
                                "status": "CONFLICT",
                                "server_updated_at": (
                                    existing_exercise.server_updated_at
                                    or existing_exercise.updated_at
                                ).isoformat(),
                                "revision": existing_exercise.revision,
                                "remote_exercise": asdict(existing_exercise),
                            }
                        )
                        continue
                    if action == "DELETE":
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
                    elif action == "UPSERT":
                        exercise = Exercise(
                            id=exercise_id,
                            owner_user_id=user_id,
                            name=ExerciseService._validate_name(str(payload.get("name", ""))),
                            description=str(payload.get("description", "")),
                            primary_muscle_group=str(
                                payload.get("primary_muscle_group", "Unspecified")
                            ),
                            equipment=str(payload.get("equipment", "None")),
                            tracking_type=TrackingType(
                                str(payload.get("tracking_type", TrackingType.REPS_WEIGHT))
                            ),
                            notes=str(payload.get("notes", "")),
                            sync_status=SyncStatus.SYNCED,
                            created_at=existing_exercise.created_at if existing_exercise else now,
                            updated_at=now,
                            server_updated_at=now,
                            deleted_at=None,
                            revision=(existing_exercise.revision + 1) if existing_exercise else 1,
                        )
                        exercise = await uow.exercises.upsert(exercise)
                    else:
                        raise ValidationAppError(f"Unsupported exercise action: {action}")
                    await uow.exercises.record_change(exercise, now)
                elif entity_type == "workout" and action == "UPSERT":
                    workout_id = UUID(str(payload["id"]))
                    aggregate_id = workout_id
                    existing_workout = await uow.workouts.get(user_id, workout_id)
                    exercise_ids_value = payload.get("exercise_ids", [])
                    if not isinstance(exercise_ids_value, list):
                        raise ValidationAppError("workout.exercise_ids must be a list")
                    synced_exercise_ids = [UUID(str(value)) for value in exercise_ids_value]
                    links = [
                        WorkoutExercise(
                            id=self._ids.new(),
                            workout_id=workout_id,
                            exercise_id=exercise_id,
                            position=position,
                        )
                        for position, exercise_id in enumerate(synced_exercise_ids)
                    ]
                    workout = Workout(
                        id=workout_id,
                        owner_user_id=user_id,
                        title=str(payload.get("title", "Workout")).strip() or "Workout",
                        status=WorkoutStatus(str(payload.get("status", WorkoutStatus.PLANNED))),
                        start_time=None,
                        end_time=None,
                        notes=str(payload.get("notes", "")),
                        sync_status=SyncStatus.SYNCED,
                        created_at=existing_workout.created_at if existing_workout else now,
                        updated_at=now,
                        exercises=links,
                        server_updated_at=now,
                    )
                    await uow.workouts.upsert(workout)
                else:
                    raise ValidationAppError(f"Unsupported sync operation: {entity_type}/{action}")
                results.append(
                    {
                        "operation_id": str(operation_id),
                        "aggregate_id": str(aggregate_id),
                        "status": "SYNCED",
                        "server_updated_at": now.isoformat(),
                        "revision": exercise.revision if entity_type == "exercise" else None,
                    }
                )
            await uow.commit()
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
