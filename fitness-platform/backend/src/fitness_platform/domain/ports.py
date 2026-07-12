from collections.abc import Sequence
from contextlib import AbstractAsyncContextManager
from datetime import datetime
from typing import Protocol
from uuid import UUID

from fitness_platform.domain.models import CatalogExercise, Exercise, GuestSession, Profile, Workout


class UserRepository(Protocol):
    async def create_guest(self, user_id: UUID, created_at: datetime) -> None: ...

    async def exists(self, user_id: UUID) -> bool: ...


class GuestSessionRepository(Protocol):
    async def try_lock_installation(self, installation_id: UUID) -> bool: ...

    async def add(self, session: GuestSession) -> None: ...

    async def find_active_by_token_hash(
        self, token_hash: str, now: datetime
    ) -> GuestSession | None: ...

    async def find_by_installation(self, installation_id: UUID) -> GuestSession | None: ...

    async def update_credentials(self, session: GuestSession, expected_token_hash: str) -> bool: ...


class ProfileRepository(Protocol):
    async def get(self, user_id: UUID) -> Profile | None: ...

    async def upsert(self, profile: Profile) -> Profile: ...


class ExerciseRepository(Protocol):
    async def is_id_taken(self, exercise_id: UUID) -> bool: ...
    async def list(self, user_id: UUID) -> Sequence[Exercise]: ...

    async def get(self, user_id: UUID, exercise_id: UUID) -> Exercise | None: ...
    async def get_including_deleted(self, user_id: UUID, exercise_id: UUID) -> Exercise | None: ...

    async def upsert(self, exercise: Exercise) -> Exercise: ...

    async def soft_delete(self, user_id: UUID, exercise_id: UUID, deleted_at: datetime) -> bool: ...
    async def record_change(self, exercise: Exercise, changed_at: datetime) -> int: ...
    async def changes_since(
        self, user_id: UUID, cursor: int, limit: int
    ) -> Sequence[tuple[int, Exercise]]: ...


class WorkoutRepository(Protocol):
    async def is_id_taken(self, workout_id: UUID) -> bool: ...
    async def list(self, user_id: UUID) -> Sequence[Workout]: ...

    async def get(self, user_id: UUID, workout_id: UUID) -> Workout | None: ...

    async def upsert(self, workout: Workout) -> Workout: ...


class CatalogRepository(Protocol):
    async def list(
        self,
        muscle: str | None,
        equipment: str | None,
        query: str | None = None,
        limit: int = 100,
        offset: int = 0,
    ) -> Sequence[CatalogExercise]: ...
    async def get(self, exercise_id: UUID) -> CatalogExercise | None: ...
    async def find(self, source: str, external_id: str) -> CatalogExercise | None: ...
    async def list_muscles(self) -> Sequence[tuple[str, str]]: ...
    async def list_equipment(self) -> Sequence[tuple[str, str]]: ...
    async def upsert(self, exercise: CatalogExercise) -> CatalogExercise: ...
    async def upsert_muscles(self, values: Sequence[tuple[str, str]]) -> None: ...
    async def upsert_equipment(self, values: Sequence[tuple[str, str]]) -> None: ...


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
    async def reserve(
        self,
        *,
        scope: str,
        key: str,
        request_hash: str,
        now: datetime,
        expires_at: datetime,
        lease_expires_at: datetime,
    ) -> tuple[str, str, int | None, dict[str, object] | None]: ...

    async def complete(
        self, scope: str, key: str, status: int, body: dict[str, object], now: datetime
    ) -> None: ...

    async def fail(self, scope: str, key: str, now: datetime) -> None: ...


class UnitOfWork(Protocol, AbstractAsyncContextManager["UnitOfWork"]):
    users: UserRepository
    guest_sessions: GuestSessionRepository
    profiles: ProfileRepository
    exercises: ExerciseRepository
    workouts: WorkoutRepository
    catalog: CatalogRepository
    outbox: OutboxRepository
    idempotency: IdempotencyRepository

    async def commit(self) -> None: ...

    async def rollback(self) -> None: ...
