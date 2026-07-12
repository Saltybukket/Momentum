from datetime import datetime
from typing import Any
from uuid import UUID

from sqlalchemy import (
    JSON,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
    Uuid,
)
from sqlalchemy import Enum as SqlEnum
from sqlalchemy.orm import Mapped, mapped_column, relationship

from fitness_platform.core.database import Base
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


def enum_column(enum_type: type[Any]) -> SqlEnum:
    return SqlEnum(enum_type, native_enum=False, validate_strings=True)


class UserRow(Base):
    __tablename__ = "users"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True)
    kind: Mapped[UserKind] = mapped_column(enum_column(UserKind), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class GuestSessionRow(Base):
    __tablename__ = "guest_sessions"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True)
    user_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )
    token_hash: Mapped[str] = mapped_column(String(64), nullable=False, unique=True)
    expires_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, index=True
    )
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    installation_id: Mapped[UUID | None] = mapped_column(
        Uuid(as_uuid=True), unique=True, index=True
    )
    recovery_secret_hash: Mapped[str | None] = mapped_column(String(64))


class ProfileRow(Base):
    __tablename__ = "profiles"

    user_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True
    )
    display_name: Mapped[str] = mapped_column(String(80), nullable=False)
    unit_system: Mapped[UnitSystem] = mapped_column(enum_column(UnitSystem), nullable=False)
    onboarding_status: Mapped[OnboardingStatus] = mapped_column(
        enum_column(OnboardingStatus), nullable=False
    )
    sync_status: Mapped[SyncStatus] = mapped_column(enum_column(SyncStatus), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, index=True
    )


class ExerciseRow(Base):
    __tablename__ = "exercises"
    __table_args__ = (
        Index("ix_exercises_owner_updated", "owner_user_id", "updated_at"),
        Index("ix_exercises_owner_deleted", "owner_user_id", "deleted_at"),
    )

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True)
    owner_user_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False, default="")
    primary_muscle_group: Mapped[str] = mapped_column(String(80), nullable=False)
    equipment: Mapped[str] = mapped_column(String(80), nullable=False)
    tracking_type: Mapped[TrackingType] = mapped_column(enum_column(TrackingType), nullable=False)
    notes: Mapped[str] = mapped_column(Text, nullable=False, default="")
    sync_status: Mapped[SyncStatus] = mapped_column(enum_column(SyncStatus), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    server_updated_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    revision: Mapped[int] = mapped_column(Integer, nullable=False, default=1)


class ExerciseChangeRow(Base):
    __tablename__ = "exercise_changes"
    __table_args__ = (Index("ix_exercise_changes_owner_sequence", "owner_user_id", "sequence"),)

    sequence: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    owner_user_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    exercise_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("exercises.id", ondelete="CASCADE"), nullable=False
    )
    revision: Mapped[int] = mapped_column(Integer, nullable=False)
    changed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class CatalogExerciseRow(Base):
    __tablename__ = "catalog_exercises"
    __table_args__ = (
        UniqueConstraint("source", "external_id", name="uq_catalog_exercise_source_external_id"),
        Index("ix_catalog_exercises_status_reviewed", "status", "reviewed"),
        CheckConstraint(
            "status IN ('DRAFT','PUBLISHED','DEPRECATED')", name="ck_catalog_exercise_status"
        ),
    )

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True)
    external_id: Mapped[str] = mapped_column(String(120), nullable=False)
    source: Mapped[str] = mapped_column(String(120), nullable=False)
    provenance: Mapped[str] = mapped_column(Text, nullable=False)
    license_name: Mapped[str] = mapped_column(String(160), nullable=False)
    license_url: Mapped[str] = mapped_column(String(500), nullable=False)
    version: Mapped[str] = mapped_column(String(40), nullable=False)
    status: Mapped[CatalogStatus] = mapped_column(enum_column(CatalogStatus), nullable=False)
    reviewed: Mapped[bool] = mapped_column(nullable=False)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False)
    tracking_type: Mapped[TrackingType] = mapped_column(enum_column(TrackingType), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    muscles: Mapped[list["CatalogExerciseMuscleRow"]] = relationship(cascade="all, delete-orphan")
    equipment: Mapped[list["CatalogExerciseEquipmentRow"]] = relationship(
        cascade="all, delete-orphan"
    )


class CatalogReleaseRow(Base):
    __tablename__ = "catalog_releases"
    __table_args__ = (
        CheckConstraint("status IN ('PUBLISHED','RETIRED')", name="ck_catalog_release_status"),
    )

    catalog_version: Mapped[str] = mapped_column(String(80), primary_key=True)
    schema_version: Mapped[str] = mapped_column(String(20), nullable=False)
    content_hash: Mapped[str] = mapped_column(String(71), nullable=False, unique=True)
    published_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    batch_id: Mapped[str] = mapped_column(String(120), nullable=False, unique=True)
    sources: Mapped[list[str]] = mapped_column(JSON, nullable=False)
    licenses: Mapped[list[str]] = mapped_column(JSON, nullable=False)
    exercise_count: Mapped[int] = mapped_column(Integer, nullable=False)
    status: Mapped[str] = mapped_column(String(20), nullable=False)


class MuscleRow(Base):
    __tablename__ = "muscles"
    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True)
    slug: Mapped[str] = mapped_column(String(80), nullable=False, unique=True)
    name: Mapped[str] = mapped_column(String(120), nullable=False)


class EquipmentRow(Base):
    __tablename__ = "equipment"
    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True)
    slug: Mapped[str] = mapped_column(String(80), nullable=False, unique=True)
    name: Mapped[str] = mapped_column(String(120), nullable=False)


class CatalogExerciseMuscleRow(Base):
    __tablename__ = "catalog_exercise_muscles"
    __table_args__ = (
        Index("ix_catalog_exercise_muscles_muscle", "muscle_id", "exercise_id"),
        CheckConstraint("role IN ('PRIMARY','SECONDARY')", name="ck_catalog_exercise_muscle_role"),
    )
    exercise_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("catalog_exercises.id", ondelete="CASCADE"), primary_key=True
    )
    muscle_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("muscles.id", ondelete="RESTRICT"), primary_key=True
    )
    role: Mapped[MuscleRole] = mapped_column(enum_column(MuscleRole), nullable=False)


class CatalogExerciseEquipmentRow(Base):
    __tablename__ = "catalog_exercise_equipment"
    __table_args__ = (
        Index("ix_catalog_exercise_equipment_equipment", "equipment_id", "exercise_id"),
    )
    exercise_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("catalog_exercises.id", ondelete="CASCADE"), primary_key=True
    )
    equipment_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("equipment.id", ondelete="RESTRICT"), primary_key=True
    )


class WorkoutRow(Base):
    __tablename__ = "workouts"
    __table_args__ = (Index("ix_workouts_owner_updated", "owner_user_id", "updated_at"),)

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True)
    owner_user_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    title: Mapped[str] = mapped_column(String(120), nullable=False)
    status: Mapped[WorkoutStatus] = mapped_column(enum_column(WorkoutStatus), nullable=False)
    start_time: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    end_time: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    notes: Mapped[str] = mapped_column(Text, nullable=False, default="")
    sync_status: Mapped[SyncStatus] = mapped_column(enum_column(SyncStatus), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    server_updated_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    exercise_links: Mapped[list["WorkoutExerciseRow"]] = relationship(
        back_populates="workout",
        cascade="all, delete-orphan",
        lazy="selectin",
        order_by="WorkoutExerciseRow.position",
    )


class WorkoutExerciseRow(Base):
    __tablename__ = "workout_exercises"
    __table_args__ = (
        UniqueConstraint("workout_id", "position", name="uq_workout_exercise_position"),
    )

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True)
    workout_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("workouts.id", ondelete="CASCADE"), nullable=False
    )
    exercise_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("exercises.id", ondelete="RESTRICT"), nullable=False
    )
    position: Mapped[int] = mapped_column(Integer, nullable=False)

    workout: Mapped[WorkoutRow] = relationship(back_populates="exercise_links")


class OutboxEventRow(Base):
    __tablename__ = "outbox_events"

    event_id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True)
    aggregate_type: Mapped[str] = mapped_column(String(80), nullable=False, index=True)
    aggregate_id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), nullable=False, index=True)
    event_type: Mapped[str] = mapped_column(String(120), nullable=False, index=True)
    payload: Mapped[dict[str, object]] = mapped_column(JSON, nullable=False)
    occurred_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, index=True
    )
    processed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), index=True)
    attempts: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    last_error: Mapped[str | None] = mapped_column(Text)


class IdempotencyRecordRow(Base):
    __tablename__ = "idempotency_records"
    __table_args__ = (
        UniqueConstraint("scope", "key", name="uq_idempotency_scope_key"),
        Index("ix_idempotency_expires_at", "expires_at"),
    )

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True)
    scope: Mapped[str] = mapped_column(String(120), nullable=False)
    key: Mapped[str] = mapped_column(String(160), nullable=False)
    request_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    state: Mapped[str] = mapped_column(String(20), nullable=False)
    response_status: Mapped[int | None] = mapped_column(Integer)
    response_body: Mapped[dict[str, object] | None] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    lease_expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class ProcessedSyncOperationRow(Base):
    __tablename__ = "processed_sync_operations"
    __table_args__ = (Index("ix_processed_sync_operations_expires", "expires_at"),)

    owner_user_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True
    )
    operation_id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True)
    request_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    result: Mapped[dict[str, object] | None] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
