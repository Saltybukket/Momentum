import re
import sys
from types import SimpleNamespace

from fitness_platform import maintenance


def test_default_worker_id_is_safe_and_unique() -> None:
    first = maintenance._default_worker_id()
    second = maintenance._default_worker_id()
    assert first != second
    assert re.fullmatch(r"[A-Za-z0-9._:@-]{1,120}", first)


async def test_idempotency_cleanup_prints_machine_readable_report(monkeypatch, capsys) -> None:
    calls: list[object] = []

    class FakeIdempotency:
        async def cleanup_expired(self, limit: int) -> int:
            calls.append(limit)
            return 7

    class FakeCloseable:
        async def close(self) -> None:
            calls.append("redis-closed")

        async def dispose(self) -> None:
            calls.append("database-disposed")

    container = SimpleNamespace(
        idempotency=FakeIdempotency(),
        redis=FakeCloseable(),
        database=FakeCloseable(),
    )
    monkeypatch.setattr(maintenance.AppContainer, "build", lambda settings: container)
    monkeypatch.setattr(maintenance, "get_settings", lambda: object())

    await maintenance._cleanup_idempotency(25)

    assert calls == [25, "redis-closed", "database-disposed"]
    assert capsys.readouterr().out.strip() == ('{"operation": "idempotency-cleanup", "deleted": 7}')


async def test_outbox_process_prints_machine_readable_report(monkeypatch, capsys) -> None:
    calls: list[object] = []

    class FakeProcessor:
        async def process(self, worker_id: str, limit: int) -> dict[str, int]:
            calls.extend([worker_id, limit])
            return {
                "claimed": 2,
                "processed": 1,
                "failed": 1,
                "dead_letter": 0,
                "lost_claim": 0,
            }

    class FakeCloseable:
        async def close(self) -> None:
            calls.append("redis-closed")

        async def dispose(self) -> None:
            calls.append("database-disposed")

    container = SimpleNamespace(
        outbox_processor=FakeProcessor(),
        redis=FakeCloseable(),
        database=FakeCloseable(),
    )
    monkeypatch.setattr(maintenance.AppContainer, "build", lambda settings: container)
    monkeypatch.setattr(maintenance, "get_settings", lambda: object())
    await maintenance._process_outbox(25, "test-worker")
    assert calls == ["test-worker", 25, "redis-closed", "database-disposed"]
    assert capsys.readouterr().out.strip() == (
        '{"claimed": 2, "dead_letter": 0, "failed": 1, "lost_claim": 0, '
        '"operation": "outbox-process", "processed": 1}'
    )


def test_main_parses_cleanup_limit(monkeypatch) -> None:
    captured: list[object] = []

    def fake_run(coroutine) -> None:
        captured.append(coroutine)
        coroutine.close()

    monkeypatch.setattr(sys, "argv", ["maintenance", "idempotency-cleanup", "--limit", "25"])
    monkeypatch.setattr(maintenance.asyncio, "run", fake_run)
    maintenance.main()
    assert len(captured) == 1
