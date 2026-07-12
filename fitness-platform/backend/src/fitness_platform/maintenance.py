import argparse
import asyncio
import json
import os
import re
import socket
from uuid import uuid4

from fitness_platform.container import AppContainer
from fitness_platform.core.config import get_settings


def _default_worker_id() -> str:
    hostname = re.sub(r"[^A-Za-z0-9._-]", "-", socket.gethostname())[:40] or "host"
    return f"{hostname}-{os.getpid()}-{uuid4()}"


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


async def _process_outbox(limit: int, worker_id: str) -> None:
    container = AppContainer.build(get_settings())
    try:
        report = await container.outbox_processor.process(worker_id, limit)
        print(json.dumps({"operation": "outbox-process", **report}, sort_keys=True))
    finally:
        await container.redis.close()
        await container.database.dispose()


def main() -> None:
    parser = argparse.ArgumentParser(description="Momentum maintenance commands")
    parser.add_argument(
        "command", choices=["idempotency-cleanup", "sync-operation-cleanup", "outbox-process"]
    )
    parser.add_argument("--limit", type=int, default=500)
    parser.add_argument("--worker-id")
    args = parser.parse_args()
    if args.command == "idempotency-cleanup":
        asyncio.run(_cleanup_idempotency(args.limit))
    elif args.command == "sync-operation-cleanup":
        asyncio.run(_cleanup_sync_operations(args.limit))
    else:
        asyncio.run(_process_outbox(args.limit, args.worker_id or _default_worker_id()))


if __name__ == "__main__":
    main()
