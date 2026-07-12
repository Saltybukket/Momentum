from collections.abc import Callable
from datetime import timedelta
from uuid import UUID

import structlog

from fitness_platform.core.clock import Clock
from fitness_platform.domain.events import (
    DomainEvent,
    EventDispatcher,
    ExerciseCreated,
    GuestProfileCreated,
    WorkoutCompleted,
    WorkoutCreated,
    WorkoutStarted,
)
from fitness_platform.domain.models import OutboxRecord
from fitness_platform.domain.ports import UnitOfWork

logger = structlog.get_logger()
UowFactory = Callable[[], UnitOfWork]


class OutboxProcessor:
    def __init__(self, uow_factory: UowFactory, clock: Clock, dispatcher: EventDispatcher) -> None:
        self._uow_factory = uow_factory
        self._clock = clock
        self._dispatcher = dispatcher

    async def process(self, worker_id: str, limit: int = 100) -> dict[str, int]:
        if not worker_id or len(worker_id) > 120:
            raise ValueError("worker_id must contain 1-120 characters")
        if not 1 <= limit <= 1000:
            raise ValueError("limit must be between 1 and 1000")
        now = self._clock.now()
        async with self._uow_factory() as uow:
            claimed = await uow.outbox.claim_due(
                worker_id=worker_id,
                now=now,
                lease_expires_at=now + timedelta(minutes=5),
                limit=limit,
            )
            await uow.commit()
        counts = {"claimed": len(claimed), "processed": 0, "failed": 0, "dead_letter": 0}
        for record in claimed:
            try:
                event = self._event_from_record(record)
                await self._dispatcher.dispatch(event)
                async with self._uow_factory() as uow:
                    if not await uow.outbox.mark_processed(
                        record.event_id, worker_id, self._clock.now()
                    ):
                        raise RuntimeError("Outbox claim was lost before completion.")
                    await uow.commit()
                counts["processed"] += 1
            except Exception as exc:
                async with self._uow_factory() as uow:
                    state = await uow.outbox.mark_failed(
                        record.event_id, worker_id, self._clock.now(), type(exc).__name__
                    )
                    await uow.commit()
                if state == "DEAD_LETTER":
                    counts["dead_letter"] += 1
                else:
                    counts["failed"] += 1
                await logger.aerror(
                    "outbox_delivery_failed",
                    event_id=str(record.event_id),
                    event_type=record.event_type,
                    state=state,
                    error_type=type(exc).__name__,
                )
        await logger.ainfo("outbox_batch_completed", worker_id=worker_id, **counts)
        return counts

    @staticmethod
    def _event_from_record(record: OutboxRecord) -> DomainEvent:
        payload = record.payload
        user_id = UUID(str(payload["user_id"]))
        if record.event_type == "GuestProfileCreated":
            return GuestProfileCreated(
                event_id=record.event_id, occurred_at=record.occurred_at, user_id=user_id
            )
        if record.event_type == "ExerciseCreated":
            return ExerciseCreated(
                event_id=record.event_id,
                occurred_at=record.occurred_at,
                exercise_id=UUID(str(payload["exercise_id"])),
                user_id=user_id,
            )
        if record.event_type == "WorkoutCreated":
            return WorkoutCreated(
                event_id=record.event_id,
                occurred_at=record.occurred_at,
                workout_id=UUID(str(payload["workout_id"])),
                user_id=user_id,
            )
        if record.event_type == "WorkoutStarted":
            return WorkoutStarted(
                event_id=record.event_id,
                occurred_at=record.occurred_at,
                workout_id=UUID(str(payload["workout_id"])),
                user_id=user_id,
            )
        if record.event_type == "WorkoutCompleted":
            return WorkoutCompleted(
                event_id=record.event_id,
                occurred_at=record.occurred_at,
                workout_id=UUID(str(payload["workout_id"])),
                user_id=user_id,
            )
        raise ValueError("Unknown outbox event type")
