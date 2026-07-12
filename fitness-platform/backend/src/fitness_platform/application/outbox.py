import asyncio
import re
from collections.abc import Callable
from datetime import timedelta
from uuid import UUID

import structlog

from fitness_platform.core.clock import Clock
from fitness_platform.core.ids import UuidProvider
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
_SAFE_WORKER_ID = re.compile(r"[A-Za-z0-9._:@-]{1,120}\Z")
_INFORMATIONAL_EVENTS = frozenset(
    {
        "GuestProfileCreated",
        "ExerciseCreated",
        "WorkoutCreated",
        "WorkoutStarted",
        "WorkoutCompleted",
    }
)


class OutboxProcessor:
    def __init__(
        self,
        uow_factory: UowFactory,
        clock: Clock,
        ids: UuidProvider,
        dispatcher: EventDispatcher,
        *,
        lease_duration: timedelta = timedelta(minutes=5),
        heartbeat_interval_seconds: float = 60.0,
        noop_event_types: frozenset[str] = _INFORMATIONAL_EVENTS,
    ) -> None:
        self._uow_factory = uow_factory
        self._clock = clock
        self._ids = ids
        self._dispatcher = dispatcher
        self._lease_duration = lease_duration
        self._heartbeat_interval_seconds = heartbeat_interval_seconds
        self._noop_event_types = noop_event_types

    async def process(self, worker_id: str, limit: int = 100) -> dict[str, int]:
        if not _SAFE_WORKER_ID.fullmatch(worker_id):
            raise ValueError("worker_id must contain 1-120 safe ASCII characters")
        if not 1 <= limit <= 1000:
            raise ValueError("limit must be between 1 and 1000")
        counts = {
            "claimed": 0,
            "processed": 0,
            "failed": 0,
            "dead_letter": 0,
            "lost_claim": 0,
        }
        for _ in range(limit):
            now = self._clock.now()
            claim_token = self._ids.new()
            async with self._uow_factory() as uow:
                claimed = await uow.outbox.claim_due(
                    worker_id=worker_id,
                    claim_token=claim_token,
                    now=now,
                    lease_expires_at=now + self._lease_duration,
                    limit=1,
                )
                await uow.commit()
            if not claimed:
                break
            counts["claimed"] += 1
            record = claimed[0]
            try:
                event = self._event_from_record(record)
                if (
                    not self._dispatcher.has_handlers(type(event))
                    and record.event_type not in self._noop_event_types
                ):
                    raise RuntimeError("Known outbox event has no registered disposition.")
                await self._dispatch_with_heartbeat(event, record.event_id, worker_id, claim_token)
                async with self._uow_factory() as uow:
                    if not await uow.outbox.mark_processed(
                        record.event_id, worker_id, claim_token, self._clock.now()
                    ):
                        counts["lost_claim"] += 1
                        await uow.commit()
                        continue
                    await uow.commit()
                counts["processed"] += 1
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                async with self._uow_factory() as uow:
                    state = await uow.outbox.mark_failed(
                        record.event_id,
                        worker_id,
                        claim_token,
                        self._clock.now(),
                        type(exc).__name__,
                    )
                    await uow.commit()
                if state == "LOST_CLAIM":
                    counts["lost_claim"] += 1
                elif state == "DEAD_LETTER":
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

    async def _dispatch_with_heartbeat(
        self, event: DomainEvent, event_id: UUID, worker_id: str, claim_token: UUID
    ) -> None:
        stopped = asyncio.Event()
        heartbeat = asyncio.create_task(self._heartbeat(stopped, event_id, worker_id, claim_token))
        try:
            await self._dispatcher.dispatch(event)
        finally:
            stopped.set()
            await heartbeat

    async def _heartbeat(
        self, stopped: asyncio.Event, event_id: UUID, worker_id: str, claim_token: UUID
    ) -> None:
        while True:
            try:
                await asyncio.wait_for(stopped.wait(), timeout=self._heartbeat_interval_seconds)
                return
            except TimeoutError:
                now = self._clock.now()
                async with self._uow_factory() as uow:
                    extended = await uow.outbox.extend_lease(
                        event_id,
                        worker_id,
                        claim_token,
                        now + self._lease_duration,
                    )
                    await uow.commit()
                if not extended:
                    return

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
