from datetime import datetime
from typing import Any
from uuid import UUID

from sqlalchemy import (
    JSON,
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
    response_status: Mapped[int] = mapped_column(Integer, nullable=False)
    response_body: Mapped[dict[str, object]] = mapped_column(JSON, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
