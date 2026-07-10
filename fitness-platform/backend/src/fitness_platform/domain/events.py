from collections import defaultdict
from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any, Protocol, TypeVar, cast
from uuid import UUID, uuid4


@dataclass(frozen=True, slots=True, kw_only=True)
class DomainEvent:
    event_id: UUID = field(default_factory=uuid4)
    occurred_at: datetime = field(default_factory=lambda: datetime.now(UTC))


@dataclass(frozen=True, slots=True, kw_only=True)
class GuestProfileCreated(DomainEvent):
    user_id: UUID


@dataclass(frozen=True, slots=True, kw_only=True)
class ExerciseCreated(DomainEvent):
    exercise_id: UUID
    user_id: UUID


@dataclass(frozen=True, slots=True, kw_only=True)
class WorkoutCreated(DomainEvent):
    workout_id: UUID
    user_id: UUID


@dataclass(frozen=True, slots=True, kw_only=True)
class WorkoutStarted(DomainEvent):
    workout_id: UUID
    user_id: UUID


@dataclass(frozen=True, slots=True, kw_only=True)
class WorkoutCompleted(DomainEvent):
    workout_id: UUID
    user_id: UUID


@dataclass(frozen=True, slots=True, kw_only=True)
class SyncOperationQueued(DomainEvent):
    operation_id: UUID
    aggregate_id: UUID


@dataclass(frozen=True, slots=True, kw_only=True)
class SyncOperationCompleted(DomainEvent):
    operation_id: UUID
    aggregate_id: UUID


EventT = TypeVar("EventT", bound=DomainEvent)
EventHandler = Callable[[Any], Awaitable[None]]


class EventDispatcher(Protocol):
    def register(
        self, event_type: type[EventT], handler: Callable[[EventT], Awaitable[None]]
    ) -> None: ...

    async def dispatch(self, event: DomainEvent) -> bool: ...


class InProcessEventDispatcher:
    """Typed in-process dispatcher with event-ID idempotency for one process lifetime."""

    def __init__(self) -> None:
        self._handlers: dict[type[DomainEvent], list[EventHandler]] = defaultdict(list)
        self._processed: set[UUID] = set()

    def register(
        self,
        event_type: type[EventT],
        handler: Callable[[EventT], Awaitable[None]],
    ) -> None:
        self._handlers[event_type].append(cast(EventHandler, handler))

    async def dispatch(self, event: DomainEvent) -> bool:
        if event.event_id in self._processed:
            return False
        for handler in self._handlers[type(event)]:
            await handler(event)
        self._processed.add(event.event_id)
        return True
