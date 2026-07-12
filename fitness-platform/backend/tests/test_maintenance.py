import sys
from types import SimpleNamespace

from fitness_platform import maintenance


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


def test_main_parses_cleanup_limit(monkeypatch) -> None:
    captured: list[object] = []

    def fake_run(coroutine) -> None:
        captured.append(coroutine)
        coroutine.close()

    monkeypatch.setattr(sys, "argv", ["maintenance", "idempotency-cleanup", "--limit", "25"])
    monkeypatch.setattr(maintenance.asyncio, "run", fake_run)
    maintenance.main()
    assert len(captured) == 1
