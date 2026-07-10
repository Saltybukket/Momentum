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

        async with self._uow_factory() as uow:
            existing = await uow.idempotency.get(scope, key)
        if existing is not None:
            existing_hash, status, body = existing
            if existing_hash != request_hash:
                raise ConflictError("Idempotency key was already used with a different request.")
            return status, body, True

        status, body = await operation()
        now = self._clock.now()
        async with self._uow_factory() as uow:
            await uow.idempotency.add(
                scope=scope,
                key=key,
                request_hash=request_hash,
                response_status=status,
                response_body=body,
                created_at=now,
                expires_at=now + timedelta(hours=24),
            )
            await uow.commit()
        return status, body, False
