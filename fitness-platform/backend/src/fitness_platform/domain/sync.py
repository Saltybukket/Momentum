import hashlib
import json
from dataclasses import asdict, dataclass
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


@dataclass(frozen=True, slots=True)
class WorkoutStartPayload:
    id: UUID


@dataclass(frozen=True, slots=True)
class WorkoutCompletePayload:
    id: UUID


SyncPayload = (
    ProfileSyncPayload
    | ExerciseUpsertPayload
    | ExerciseDeletePayload
    | WorkoutUpsertPayload
    | WorkoutStartPayload
    | WorkoutCompletePayload
)


@dataclass(frozen=True, slots=True)
class SyncCommand:
    operation_id: UUID
    entity_type: str
    action: str
    payload: SyncPayload


def canonical_request_hash(command: SyncCommand) -> str:
    contract = {
        "contract_version": "1",
        "entity_type": command.entity_type,
        "action": command.action,
        "payload": asdict(command.payload),
    }
    serialized = json.dumps(
        contract,
        default=str,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    )
    return hashlib.sha256(serialized.encode("utf-8")).hexdigest()
