from datetime import datetime
from enum import StrEnum
from typing import Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator

from fitness_platform.domain.enums import (
    OnboardingStatus,
    SyncStatus,
    TrackingType,
    UnitSystem,
    WorkoutStatus,
)
from fitness_platform.domain.models import Exercise, Profile, Workout


class ApiModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class GuestSessionCreate(ApiModel):
    display_name: str = Field(default="Guest", max_length=80)
    installation_id: UUID
    recovery_secret: str = Field(min_length=32, max_length=256)


class ProfileResponse(ApiModel):
    user_id: UUID
    display_name: str
    unit_system: UnitSystem
    onboarding_status: OnboardingStatus
    sync_status: SyncStatus
    created_at: datetime
    updated_at: datetime

    @classmethod
    def from_domain(cls, profile: Profile) -> "ProfileResponse":
        return (
            cls(**profile.__dict__)
            if hasattr(profile, "__dict__")
            else cls(
                user_id=profile.user_id,
                display_name=profile.display_name,
                unit_system=profile.unit_system,
                onboarding_status=profile.onboarding_status,
                sync_status=profile.sync_status,
                created_at=profile.created_at,
                updated_at=profile.updated_at,
            )
        )


class GuestSessionResponse(ApiModel):
    guest_token: str
    token_type: str = "Bearer"
    profile: ProfileResponse
    expires_in_seconds: int
    development_only: bool = True
    recovered: bool = False


class ProfileUpdate(ApiModel):
    display_name: str = Field(min_length=1, max_length=80)
    unit_system: UnitSystem
    onboarding_status: OnboardingStatus


class ExerciseWrite(ApiModel):
    id: UUID | None = None
    name: str = Field(min_length=1, max_length=120)
    description: str = Field(default="", max_length=4000)
    primary_muscle_group: str = Field(default="Unspecified", max_length=80)
    equipment: str = Field(default="None", max_length=80)
    tracking_type: TrackingType = TrackingType.REPS_WEIGHT
    notes: str = Field(default="", max_length=4000)

    @field_validator("name")
    @classmethod
    def name_must_not_be_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("name must not be blank")
        return value


class ExerciseResponse(ApiModel):
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
    server_updated_at: datetime | None
    revision: int
    deleted_at: datetime | None = None

    @classmethod
    def from_domain(cls, exercise: Exercise) -> "ExerciseResponse":
        return cls(
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
            revision=exercise.revision,
            deleted_at=exercise.deleted_at,
        )


class WorkoutWrite(ApiModel):
    id: UUID | None = None
    title: str = Field(min_length=1, max_length=120)
    notes: str = Field(default="", max_length=4000)
    exercise_ids: list[UUID] = Field(default_factory=list)
    status: WorkoutStatus | None = None

    @field_validator("title")
    @classmethod
    def title_must_not_be_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("title must not be blank")
        return value


class WorkoutExerciseResponse(ApiModel):
    id: UUID
    exercise_id: UUID
    position: int


class WorkoutResponse(ApiModel):
    id: UUID
    owner_user_id: UUID
    title: str
    status: WorkoutStatus
    start_time: datetime | None
    end_time: datetime | None
    notes: str
    sync_status: SyncStatus
    created_at: datetime
    updated_at: datetime
    server_updated_at: datetime | None
    exercises: list[WorkoutExerciseResponse]

    @classmethod
    def from_domain(cls, workout: Workout) -> "WorkoutResponse":
        return cls(
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
            exercises=[
                WorkoutExerciseResponse(
                    id=link.id,
                    exercise_id=link.exercise_id,
                    position=link.position,
                )
                for link in workout.exercises
            ],
        )


class PageMeta(ApiModel):
    limit: int
    offset: int
    total: int


class ExercisePage(ApiModel):
    items: list[ExerciseResponse]
    page: PageMeta


class WorkoutPage(ApiModel):
    items: list[WorkoutResponse]
    page: PageMeta


class SyncEntityType(StrEnum):
    PROFILE = "profile"
    EXERCISE = "exercise"
    WORKOUT = "workout"


class SyncAction(StrEnum):
    UPSERT = "UPSERT"
    DELETE = "DELETE"


class SyncOperation(ApiModel):
    operation_id: UUID
    entity_type: SyncEntityType
    action: SyncAction
    payload: dict[str, Any]


class SyncPushRequest(ApiModel):
    operations: list[SyncOperation] = Field(min_length=1, max_length=100)


class SyncResult(ApiModel):
    operation_id: UUID
    aggregate_id: UUID
    status: SyncStatus
    server_updated_at: datetime
    revision: int | None = None
    remote_exercise: ExerciseResponse | None = None


class SyncPushResponse(ApiModel):
    results: list[SyncResult]


class ExerciseChange(ApiModel):
    cursor: int
    deleted: bool
    exercise: ExerciseResponse


class SyncPullResponse(ApiModel):
    changes: list[ExerciseChange]
    next_cursor: int
    has_more: bool


class HealthResponse(ApiModel):
    status: str
    database: str
    redis: str
    version: str


class CatalogMuscleResponse(ApiModel):
    slug: str
    role: str


class CatalogExerciseResponse(ApiModel):
    id: UUID
    external_id: str
    source: str
    provenance: str
    license_name: str
    license_url: str
    version: str
    status: str
    reviewed: bool
    name: str
    description: str
    tracking_type: TrackingType
    muscles: list[CatalogMuscleResponse]
    equipment: list[str]

    @classmethod
    def from_domain(cls, exercise: Any) -> "CatalogExerciseResponse":
        payload = {field: getattr(exercise, field) for field in cls.model_fields}
        payload["muscles"] = [
            CatalogMuscleResponse(slug=slug, role=role.value) for slug, role in exercise.muscles
        ]
        return cls(**payload)


class CatalogFacetResponse(ApiModel):
    slug: str
    name: str


class CatalogExercisePage(ApiModel):
    items: list[CatalogExerciseResponse]
    page: PageMeta


class CatalogSnapshotResponse(ApiModel):
    schema_version: str
    catalog_version: str
    content_hash: str
    published_at: datetime
    batch_id: str
    total: int
    complete: bool = True
    sources: list[str]
    licenses: list[str]
    muscles: list[CatalogFacetResponse]
    equipment: list[CatalogFacetResponse]
    exercises: list[CatalogExerciseResponse]
