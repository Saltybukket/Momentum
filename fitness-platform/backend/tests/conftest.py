import os
from collections.abc import AsyncIterator
from pathlib import Path
from uuid import NAMESPACE_URL, uuid5

import pytest
from httpx import ASGITransport, AsyncClient

from fitness_platform.container import AppContainer
from fitness_platform.core.config import Settings
from fitness_platform.core.database import Base, Database
from fitness_platform.main import create_app


@pytest.fixture
async def app_client(tmp_path: Path) -> AsyncIterator[tuple[AsyncClient, AppContainer]]:
    database_path = tmp_path / "test.db"
    settings = Settings(
        environment="test",
        database_url=f"sqlite+aiosqlite:///{database_path}",
        redis_url="redis://127.0.0.1:6399/15",
        guest_token_pepper="test-pepper",
        rate_limit_guest_sessions_per_minute=100,
        enable_docs=False,
    )
    database = Database(settings)
    async with database.engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
    container = AppContainer.build(settings, database=database)
    app = create_app(settings, container)
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        yield client, container
    await container.redis.close()
    await container.database.dispose()


@pytest.fixture
async def postgres_app_client() -> AsyncIterator[tuple[AsyncClient, AppContainer]]:
    database_url = os.getenv("FITNESS_TEST_POSTGRES_URL")
    if not database_url:
        pytest.skip("FITNESS_TEST_POSTGRES_URL is required for PostgreSQL integration tests")
    settings = Settings(
        environment="test",
        database_url=database_url,
        redis_url="redis://127.0.0.1:6399/15",
        guest_token_pepper="test-pepper",
        rate_limit_guest_sessions_per_minute=100,
        enable_docs=False,
    )
    database = Database(settings)
    async with database.engine.begin() as connection:
        await connection.run_sync(Base.metadata.drop_all)
        await connection.run_sync(Base.metadata.create_all)
    container = AppContainer.build(settings, database=database)
    app = create_app(settings, container)
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        yield client, container
    await container.redis.close()
    await container.database.dispose()


async def create_guest(
    client: AsyncClient, *, key: str = "guest-1"
) -> tuple[str, dict[str, object]]:
    response = await client.post(
        "/api/v1/guest-sessions",
        json={
            "display_name": "Test Guest",
            "installation_id": str(uuid5(NAMESPACE_URL, f"test-installation:{key}")),
            "recovery_secret": f"test-recovery-secret-{key}-0000000000000000",
        },
    )
    assert response.status_code == 201, response.text
    body = response.json()
    return body["guest_token"], body["profile"]
