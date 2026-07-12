from datetime import UTC, datetime
from types import SimpleNamespace

import pytest

from fitness_platform.application.idempotency import IdempotencyService
from fitness_platform.core.errors import ConflictError
from fitness_platform.domain.events import DomainEvent


class FakeClock:
    def now(self) -> datetime:
        return datetime(2026, 7, 12, tzinfo=UTC)


class FakeEvents:
    def __init__(self) -> None:
        self.dispatched: list[DomainEvent] = []

    async def dispatch(self, event: DomainEvent) -> bool:
        self.dispatched.append(event)
        return True


class FakeUnitOfWork:
    def __init__(self, reservation) -> None:
        self.idempotency = SimpleNamespace(reserve=self._reserve, complete=self._complete)
        self.reservation = reservation
        self.committed = False
        self.rolled_back = False

    async def _reserve(self, **kwargs):
        return self.reservation

    async def _complete(self, *args) -> None:
        return None

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args) -> None:
        return None

    async def commit(self) -> None:
        self.committed = True

    async def rollback(self) -> None:
        self.rolled_back = True


async def test_no_key_executes_without_reservation() -> None:
    events = FakeEvents()
    service = IdempotencyService(lambda: FakeUnitOfWork(None), FakeClock(), events)

    async def operation(uow):
        assert uow is None
        return 201, {"ok": True}, None

    assert await service.execute(
        scope="POST:/test:principal=1", key=None, request_hash="hash", operation=operation
    ) == (201, {"ok": True}, False)


async def test_completed_replays_and_failed_conflicts() -> None:
    events = FakeEvents()
    completed = FakeUnitOfWork(("COMPLETED", "hash", 201, {"id": "one"}))
    service = IdempotencyService(lambda: completed, FakeClock(), events)

    async def unused(uow):
        raise AssertionError("operation must not run")

    assert await service.execute(
        scope="POST:/test:principal=1", key="key", request_hash="hash", operation=unused
    ) == (201, {"id": "one"}, True)
    failed = FakeUnitOfWork(("FAILED", "hash", None, None))
    service = IdempotencyService(lambda: failed, FakeClock(), events)
    with pytest.raises(ConflictError):
        await service.execute(
            scope="POST:/test:principal=1", key="key", request_hash="hash", operation=unused
        )


async def test_operation_failure_rolls_back() -> None:
    events = FakeEvents()
    uow = FakeUnitOfWork(("RESERVED", "hash", None, None))
    service = IdempotencyService(lambda: uow, FakeClock(), events)

    async def failing_operation(active_uow):
        raise RuntimeError("failure")

    with pytest.raises(RuntimeError):
        await service.execute(
            scope="POST:/test:principal=1",
            key="key",
            request_hash="hash",
            operation=failing_operation,
        )
    assert uow.rolled_back
