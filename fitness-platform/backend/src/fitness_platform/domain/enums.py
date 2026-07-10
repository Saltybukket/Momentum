from enum import StrEnum


class SyncStatus(StrEnum):
    LOCAL_ONLY = "LOCAL_ONLY"
    PENDING = "PENDING"
    SYNCING = "SYNCING"
    SYNCED = "SYNCED"
    FAILED = "FAILED"
    CONFLICT = "CONFLICT"


class UnitSystem(StrEnum):
    METRIC = "METRIC"
    IMPERIAL = "IMPERIAL"


class OnboardingStatus(StrEnum):
    NOT_STARTED = "NOT_STARTED"
    SKIPPED = "SKIPPED"
    IN_PROGRESS = "IN_PROGRESS"
    COMPLETED = "COMPLETED"


class TrackingType(StrEnum):
    REPS_WEIGHT = "REPS_WEIGHT"
    REPS = "REPS"
    DURATION = "DURATION"
    DISTANCE_DURATION = "DISTANCE_DURATION"


class WorkoutStatus(StrEnum):
    PLANNED = "PLANNED"
    IN_PROGRESS = "IN_PROGRESS"
    COMPLETED = "COMPLETED"
    CANCELLED = "CANCELLED"


class UserKind(StrEnum):
    GUEST = "GUEST"
    REGISTERED = "REGISTERED"
