from dataclasses import dataclass, field
from datetime import datetime
from uuid import UUID

from fitness_platform.domain.enums import (
    CatalogStatus,
    MuscleRole,
    OnboardingStatus,
    SyncStatus,
    TrackingType,
    UnitSystem,
    UserKind,
    WorkoutStatus,
)


@dataclass(slots=True)
class CatalogExercise:
    id: UUID
    external_id: str
    source: str
    provenance: str
    license_name: str
    license_url: str
    version: str
    status: CatalogStatus
    reviewed: bool
    name: str
    description: str
    tracking_type: TrackingType
    muscles: list[tuple[str, MuscleRole]]
    equipment: list[str]
    created_at: datetime
    updated_at: datetime


@dataclass(slots=True)
class User:
    id: UUID
    kind: UserKind
    created_at: datetime


@dataclass(slots=True)
class GuestSession:
    id: UUID
    user_id: UUID
    token_hash: str
    expires_at: datetime
    created_at: datetime
    revoked_at: datetime | None = None
    installation_id: UUID | None = None
    recovery_secret_hash: str | None = None


@dataclass(slots=True)
class Profile:
    user_id: UUID
    display_name: str
    unit_system: UnitSystem
    onboarding_status: OnboardingStatus
    sync_status: SyncStatus
    created_at: datetime
    updated_at: datetime


@dataclass(slots=True)
class Exercise:
    id: UUID
    owner_user_id: UUID
    name: str
    description: str
    primary_muscle_group: str
    equipment: str
    tracking_type: TrackingType
    notes: str
    sync_status: SyncStatus
    created_at: datetime
    updated_at: datetime
    server_updated_at: datetime | None = None
    deleted_at: datetime | None = None
    revision: int = 0


@dataclass(slots=True)
class WorkoutExercise:
    id: UUID
    workout_id: UUID
    exercise_id: UUID
    position: int


@dataclass(slots=True)
class Workout:
    id: UUID
    owner_user_id: UUID
    title: str
    status: WorkoutStatus
    notes: str
    sync_status: SyncStatus
    created_at: datetime
    updated_at: datetime
    start_time: datetime | None = None
    end_time: datetime | None = None
    exercises: list[WorkoutExercise] = field(default_factory=list)
    server_updated_at: datetime | None = None


@dataclass(slots=True)
class OutboxRecord:
    event_id: UUID
    aggregate_type: str
    aggregate_id: UUID
    event_type: str
    payload: dict[str, object]
    occurred_at: datetime
    processed_at: datetime | None = None
    attempts: int = 0
    last_error: str | None = None
