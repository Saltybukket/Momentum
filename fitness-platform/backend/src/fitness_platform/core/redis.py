from typing import Any, cast

from redis.asyncio import Redis

from fitness_platform.core.config import Settings


class RedisClient:
    def __init__(self, settings: Settings) -> None:
        self.client: Redis[str] = Redis.from_url(settings.redis_url, decode_responses=True)

    async def ping(self) -> bool:
        return bool(await self.client.ping())

    async def close(self) -> None:
        await cast(Any, self.client).aclose()

    async def set_if_absent(self, key: str, value: str, ttl_seconds: int) -> bool:
        result: Any = await self.client.set(key, value, ex=ttl_seconds, nx=True)
        return bool(result)
