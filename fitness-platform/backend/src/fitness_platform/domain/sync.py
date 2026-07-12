from dataclasses import dataclass
from uuid import UUID

from fitness_platform.domain.enums import OnboardingStatus, TrackingType, UnitSystem


@dataclass(frozen=True, slots=True)
class ProfileSyncPayload:
    display_name: str
    unit_system: UnitSystem
    onboarding_status: OnboardingStatus


@dataclass(frozen=True, slots=True)
class ExerciseUpsertPayload:
    id: UUID
    name: str
    description: str
    primary_muscle_group: str
    equipment: str
    tracking_type: TrackingType
    notes: str
    base_revision: int | None


@dataclass(frozen=True, slots=True)
class ExerciseDeletePayload:
    id: UUID
    base_revision: int


@dataclass(frozen=True, slots=True)
class WorkoutUpsertPayload:
    id: UUID
    title: str
    notes: str
    exercise_ids: tuple[UUID, ...]


SyncPayload = (
    ProfileSyncPayload | ExerciseUpsertPayload | ExerciseDeletePayload | WorkoutUpsertPayload
)


@dataclass(frozen=True, slots=True)
class SyncCommand:
    operation_id: UUID
    payload: SyncPayload
