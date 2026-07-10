from collections.abc import Sequence
from contextlib import AbstractAsyncContextManager
from datetime import datetime
from typing import Protocol
from uuid import UUID

from fitness_platform.domain.models import Exercise, GuestSession, Profile, Workout


class UserRepository(Protocol):
    async def create_guest(self, user_id: UUID, created_at: datetime) -> None: ...

    async def exists(self, user_id: UUID) -> bool: ...


class GuestSessionRepository(Protocol):
    async def add(self, session: GuestSession) -> None: ...

    async def find_active_by_token_hash(
        self, token_hash: str, now: datetime
    ) -> GuestSession | None: ...


class ProfileRepository(Protocol):
    async def get(self, user_id: UUID) -> Profile | None: ...

    async def upsert(self, profile: Profile) -> Profile: ...


class ExerciseRepository(Protocol):
    async def list(self, user_id: UUID) -> Sequence[Exercise]: ...

    async def get(self, user_id: UUID, exercise_id: UUID) -> Exercise | None: ...
    async def get_including_deleted(self, user_id: UUID, exercise_id: UUID) -> Exercise | None: ...

    async def upsert(self, exercise: Exercise) -> Exercise: ...

    async def soft_delete(self, user_id: UUID, exercise_id: UUID, deleted_at: datetime) -> bool: ...
    async def record_change(self, exercise: Exercise, changed_at: datetime) -> int: ...
    async def changes_since(self, user_id: UUID, cursor: int, limit: int) -> Sequence[tuple[int, Exercise]]: ...


class WorkoutRepository(Protocol):
    async def list(self, user_id: UUID) -> Sequence[Workout]: ...

    async def get(self, user_id: UUID, workout_id: UUID) -> Workout | None: ...

    async def upsert(self, workout: Workout) -> Workout: ...


class OutboxRepository(Protocol):
    async def add_event(
        self,
        *,
        event_id: UUID,
        aggregate_type: str,
        aggregate_id: UUID,
        event_type: str,
        payload: dict[str, object],
        occurred_at: datetime,
    ) -> bool: ...


class IdempotencyRepository(Protocol):
    async def get(self, scope: str, key: str) -> tuple[str, int, dict[str, object]] | None: ...

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
    ) -> None: ...


class UnitOfWork(Protocol, AbstractAsyncContextManager["UnitOfWork"]):
    users: UserRepository
    guest_sessions: GuestSessionRepository
    profiles: ProfileRepository
    exercises: ExerciseRepository
    workouts: WorkoutRepository
    outbox: OutboxRepository
    idempotency: IdempotencyRepository

    async def commit(self) -> None: ...

    async def rollback(self) -> None: ...
