import asyncio
from collections import defaultdict, deque
from datetime import UTC, datetime, timedelta

from fitness_platform.core.errors import AppError


class InMemorySlidingWindowRateLimiter:
    """Process-local development limiter.

    Production deployments should replace this with the Redis implementation documented in
    SECURITY.md. It is intentionally deterministic and safe for tests, but not distributed.
    """

    def __init__(self) -> None:
        self._events: dict[str, deque[datetime]] = defaultdict(deque)
        self._lock = asyncio.Lock()

    async def check(self, key: str, limit: int, window_seconds: int = 60) -> None:
        now = datetime.now(UTC)
        cutoff = now - timedelta(seconds=window_seconds)
        async with self._lock:
            events = self._events[key]
            while events and events[0] < cutoff:
                events.popleft()
            if len(events) >= limit:
                raise AppError("RATE_LIMITED", "Too many requests.", 429)
            events.append(now)
