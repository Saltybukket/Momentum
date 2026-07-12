import argparse
import asyncio
import json

from fitness_platform.container import AppContainer
from fitness_platform.core.config import get_settings


async def _cleanup_idempotency(limit: int) -> None:
    container = AppContainer.build(get_settings())
    try:
        deleted = await container.idempotency.cleanup_expired(limit)
        print(json.dumps({"operation": "idempotency-cleanup", "deleted": deleted}))
    finally:
        await container.redis.close()
        await container.database.dispose()


async def _cleanup_sync_operations(limit: int) -> None:
    container = AppContainer.build(get_settings())
    try:
        deleted = await container.sync.cleanup_processed_operations(limit)
        print(json.dumps({"operation": "sync-operation-cleanup", "deleted": deleted}))
    finally:
        await container.redis.close()
        await container.database.dispose()


def main() -> None:
    parser = argparse.ArgumentParser(description="Momentum maintenance commands")
    parser.add_argument("command", choices=["idempotency-cleanup", "sync-operation-cleanup"])
    parser.add_argument("--limit", type=int, default=500)
    args = parser.parse_args()
    if args.command == "idempotency-cleanup":
        asyncio.run(_cleanup_idempotency(args.limit))
    else:
        asyncio.run(_cleanup_sync_operations(args.limit))


if __name__ == "__main__":
    main()
