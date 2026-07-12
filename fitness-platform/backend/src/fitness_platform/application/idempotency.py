import hashlib
import re
from collections.abc import Awaitable, Callable
from datetime import timedelta

from fitness_platform.core.clock import Clock
from fitness_platform.core.errors import ConflictError
from fitness_platform.domain.events import DomainEvent, EventDispatcher
from fitness_platform.domain.ports import UnitOfWork


class IdempotencyService:
    def __init__(
        self,
        uow_factory: Callable[[], UnitOfWork],
        clock: Clock,
        event_dispatcher: EventDispatcher,
    ) -> None:
        self._uow_factory = uow_factory
        self._clock = clock
        self._events = event_dispatcher

    @staticmethod
    def storage_key(scope: str, key: str) -> str:
        return hashlib.sha256(f"{scope}\0{key}".encode()).hexdigest()

    async def cleanup_expired(self, batch_limit: int = 500) -> int:
        if not 1 <= batch_limit <= 10_000:
            raise ValueError("batch_limit must be between 1 and 10000")
        async with self._uow_factory() as uow:
            deleted = await uow.idempotency.delete_expired(self._clock.now(), batch_limit)
            await uow.commit()
        return deleted

    async def execute(
        self,
        *,
        scope: str,
        key: str | None,
        request_hash: str,
        operation: Callable[
            [UnitOfWork | None],
            Awaitable[tuple[int, dict[str, object], DomainEvent | None]],
        ],
    ) -> tuple[int, dict[str, object], bool]:
        if not key:
            status, body, _ = await operation(None)
            return status, body, False

        now = self._clock.now()
        if not re.fullmatch(r"[A-Za-z0-9._:-]{1,128}", key):
            raise ConflictError("Idempotency key must contain 1-128 safe ASCII characters.")
        stored_key = self.storage_key(scope, key)
        event: DomainEvent | None = None
        async with self._uow_factory() as uow:
            state, existing_hash, response_status, response_body = await uow.idempotency.reserve(
                scope=scope,
                key=stored_key,
                request_hash=request_hash,
                now=now,
                expires_at=now + timedelta(hours=24),
                lease_expires_at=now + timedelta(minutes=5),
            )
            if existing_hash != request_hash:
                raise ConflictError("Idempotency key was already used with a different request.")
            if state == "COMPLETED" and response_status is not None and response_body is not None:
                await uow.commit()
                return response_status, response_body, True
            if state != "RESERVED":
                raise ConflictError(
                    "Idempotent operation is already running or failed; retry with a new key."
                )
            try:
                status, body, event = await operation(uow)
                await uow.idempotency.complete(scope, stored_key, status, body, self._clock.now())
                await uow.commit()
            except Exception:
                await uow.rollback()
                raise
        if event is not None:
            await self._events.dispatch(event)
        return status, body, False
