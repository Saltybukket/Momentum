import re
from collections.abc import Awaitable, Callable
from datetime import timedelta

from fitness_platform.core.clock import Clock
from fitness_platform.core.errors import ConflictError
from fitness_platform.domain.ports import UnitOfWork


class IdempotencyService:
    def __init__(self, uow_factory: Callable[[], UnitOfWork], clock: Clock) -> None:
        self._uow_factory = uow_factory
        self._clock = clock

    async def execute(
        self,
        *,
        scope: str,
        key: str | None,
        request_hash: str,
        operation: Callable[[], Awaitable[tuple[int, dict[str, object]]]],
    ) -> tuple[int, dict[str, object], bool]:
        if not key:
            status, body = await operation()
            return status, body, False

        now = self._clock.now()
        if not re.fullmatch(r"[A-Za-z0-9._:-]{1,128}", key):
            raise ConflictError("Idempotency key must contain 1-128 safe ASCII characters.")
        async with self._uow_factory() as uow:
            state, existing_hash, response_status, response_body = await uow.idempotency.reserve(
                scope=scope,
                key=key,
                request_hash=request_hash,
                now=now,
                expires_at=now + timedelta(hours=24),
                lease_expires_at=now + timedelta(minutes=5),
            )
            await uow.commit()
        if existing_hash != request_hash:
            raise ConflictError("Idempotency key was already used with a different request.")
        if state == "COMPLETED" and response_status is not None and response_body is not None:
            return response_status, response_body, True
        if state != "RESERVED":
            raise ConflictError(
                "Idempotent operation is already running or failed; retry with a new key."
            )
        try:
            status, body = await operation()
        except Exception:
            async with self._uow_factory() as uow:
                await uow.idempotency.fail(scope, key, self._clock.now())
                await uow.commit()
            raise
        async with self._uow_factory() as uow:
            await uow.idempotency.complete(scope, key, status, body, self._clock.now())
            await uow.commit()
        return status, body, False
