import asyncio
import os
import re
import subprocess
import sys
from datetime import UTC, datetime
from pathlib import Path
from uuid import NAMESPACE_URL, uuid5

import pytest
from sqlalchemy import MetaData, create_engine, inspect, text
from sqlalchemy.engine import Connection, make_url
from sqlalchemy.ext.asyncio import create_async_engine


@pytest.mark.integration
def test_migrations_apply_to_empty_database(tmp_path: Path) -> None:
    backend_dir = Path(__file__).resolve().parents[1]
    database_path = tmp_path / "migration.db"
    database_url = f"sqlite+aiosqlite:///{database_path}"
    env = os.environ | {
        "FITNESS_ENVIRONMENT": "test",
        "FITNESS_DATABASE_URL": database_url,
        "FITNESS_GUEST_TOKEN_PEPPER": "migration-test",
    }
    result = subprocess.run(
        [sys.executable, "-m", "alembic", "upgrade", "head"],
        cwd=backend_dir,
        env=env,
        capture_output=True,
        text=True,
        check=False,
    )
    assert result.returncode == 0, result.stdout + result.stderr

    engine = create_engine(f"sqlite:///{database_path}")
    tables = set(inspect(engine).get_table_names())
    engine.dispose()
    assert {
        "users",
        "guest_sessions",
        "profiles",
        "exercises",
        "workouts",
        "workout_exercises",
        "outbox_events",
        "idempotency_records",
    }.issubset(tables)


@pytest.mark.integration
@pytest.mark.parametrize("legacy_key", ["raw-client-key", "a" * 64])
def test_idempotency_migration_purges_ambiguous_legacy_keys(
    tmp_path: Path, legacy_key: str
) -> None:
    backend_dir = Path(__file__).resolve().parents[1]
    database_path = tmp_path / "idempotency-migration.db"
    sync_url = f"sqlite:///{database_path}"
    env = os.environ | {
        "FITNESS_ENVIRONMENT": "test",
        "FITNESS_DATABASE_URL": f"sqlite+aiosqlite:///{database_path}",
        "FITNESS_GUEST_TOKEN_PEPPER": "migration-test",
    }
    subprocess.run(
        [sys.executable, "-m", "alembic", "upgrade", "d8f2a1c7e904"],
        cwd=backend_dir,
        env=env,
        check=True,
    )
    engine = create_engine(sync_url)
    with engine.begin() as connection:
        connection.execute(
            text(
                "INSERT INTO idempotency_records "
                "(id, scope, key, request_hash, state, created_at, updated_at, "
                "lease_expires_at, expires_at) VALUES "
                "(:id, :scope, :key, :request_hash, 'IN_PROGRESS', :now, :now, :now, :now)"
            ),
            {
                "id": "88be75ba-0a72-4726-82bc-2b6e8cc02760",
                "scope": "POST:/resource:principal=test",
                "key": legacy_key,
                "request_hash": "0" * 64,
                "now": "2026-07-12 00:00:00",
            },
        )
    engine.dispose()
    subprocess.run(
        [sys.executable, "-m", "alembic", "upgrade", "head"],
        cwd=backend_dir,
        env=env,
        check=True,
    )
    verification_engine = create_engine(sync_url)
    with verification_engine.connect() as connection:
        stored = connection.scalar(text("SELECT key FROM idempotency_records"))
    verification_engine.dispose()
    assert stored is None


def _alembic(backend_dir: Path, database_url: str, *arguments: str) -> None:
    env = os.environ | {
        "FITNESS_ENVIRONMENT": "test",
        "FITNESS_DATABASE_URL": database_url,
        "FITNESS_GUEST_TOKEN_PEPPER": "migration-test",
    }
    result = subprocess.run(  # noqa: S603 - fixed interpreter and test-owned Alembic arguments
        [sys.executable, "-m", "alembic", *arguments],
        cwd=backend_dir,
        env=env,
        capture_output=True,
        text=True,
        check=False,
    )
    assert result.returncode == 0, result.stdout + result.stderr


def _seed_release_history(connection: Connection) -> None:
    metadata = MetaData()
    metadata.reflect(connection)
    releases = metadata.tables["catalog_releases"]
    activation = metadata.tables["catalog_activation"]
    muscles = metadata.tables["catalog_release_muscles"]
    equipment = metadata.tables["catalog_release_equipment"]
    exercises = metadata.tables["catalog_release_exercises"]
    exercise_muscles = metadata.tables["catalog_release_exercise_muscles"]
    exercise_equipment = metadata.tables["catalog_release_exercise_equipment"]
    now = datetime(2026, 7, 13, tzinfo=UTC)
    release_specs = (
        ("roundtrip-v1", "ACTIVE", "active-squat"),
        ("roundtrip-v2", "RETIRED", "newer-row"),
    )
    connection.execute(
        releases.insert(),
        [
            {
                "catalog_version": version,
                "schema_version": "1",
                "content_hash": "sha256:" + ("1" if version.endswith("v1") else "2") * 64,
                "published_at": now,
                "batch_id": version,
                "sources": ["test"],
                "licenses": ["CC0-1.0"],
                "exercise_count": 1,
                "status": status,
            }
            for version, status, _external_id in release_specs
        ],
    )
    for version, _status, external_id in release_specs:
        exercise_id = uuid5(NAMESPACE_URL, f"momentum-catalog:test:{external_id}")
        stored_exercise_id = exercise_id.hex if connection.dialect.name == "sqlite" else exercise_id
        connection.execute(
            muscles.insert(),
            {"catalog_version": version, "slug": "legs", "name": "Legs"},
        )
        connection.execute(
            equipment.insert(),
            {"catalog_version": version, "slug": "none", "name": "No equipment"},
        )
        connection.execute(
            exercises.insert(),
            {
                "catalog_version": version,
                "id": stored_exercise_id,
                "external_id": external_id,
                "source": "test",
                "provenance": "Self-authored migration fixture.",
                "license_name": "CC0-1.0",
                "license_url": "https://creativecommons.org/publicdomain/zero/1.0/",
                "version": "1",
                "status": "PUBLISHED",
                "reviewed": True,
                "name": external_id,
                "description": "Migration fixture.",
                "tracking_type": "REPS",
                "created_at": now,
                "updated_at": now,
            },
        )
        connection.execute(
            exercise_muscles.insert(),
            {
                "catalog_version": version,
                "exercise_id": stored_exercise_id,
                "muscle_slug": "legs",
                "role": "PRIMARY",
            },
        )
        connection.execute(
            exercise_equipment.insert(),
            {
                "catalog_version": version,
                "exercise_id": stored_exercise_id,
                "equipment_slug": "none",
            },
        )
    connection.execute(
        activation.update()
        .where(activation.c.singleton_id == 1)
        .values(catalog_version="roundtrip-v1")
    )


async def _seed(database_url: str) -> None:
    engine = create_async_engine(database_url)
    async with engine.begin() as connection:
        await connection.run_sync(_seed_release_history)
    await engine.dispose()


def _seed_empty_release(connection: Connection) -> None:
    metadata = MetaData()
    metadata.reflect(connection)
    releases = metadata.tables["catalog_releases"]
    activation = metadata.tables["catalog_activation"]
    connection.execute(
        releases.insert(),
        {
            "catalog_version": "empty-v1",
            "schema_version": "1",
            "content_hash": "sha256:" + "0" * 64,
            "published_at": datetime(2026, 7, 13, tzinfo=UTC),
            "batch_id": "empty-v1",
            "sources": [],
            "licenses": [],
            "exercise_count": 0,
            "status": "ACTIVE",
        },
    )
    connection.execute(
        activation.update().where(activation.c.singleton_id == 1).values(catalog_version="empty-v1")
    )


async def _seed_empty(database_url: str) -> None:
    engine = create_async_engine(database_url)
    async with engine.begin() as connection:
        await connection.run_sync(_seed_empty_release)
    await engine.dispose()


async def _values(database_url: str, statement: str) -> list[tuple[object, ...]]:
    engine = create_async_engine(database_url)
    async with engine.connect() as connection:
        rows = (await connection.execute(text(statement))).tuples().all()
    await engine.dispose()
    return [tuple(row) for row in rows]


def _assert_catalog_round_trip(backend_dir: Path, database_url: str) -> None:
    _alembic(backend_dir, database_url, "upgrade", "head")
    asyncio.run(_seed(database_url))
    _alembic(backend_dir, database_url, "downgrade", "f26c8d0e531a")
    assert asyncio.run(
        _values(
            database_url,
            "SELECT external_id FROM catalog_exercises ORDER BY external_id",
        )
    ) == [("active-squat",)]
    assert asyncio.run(
        _values(
            database_url,
            "SELECT catalog_version, status FROM catalog_releases ORDER BY catalog_version",
        )
    ) == [("roundtrip-v1", "PUBLISHED"), ("roundtrip-v2", "RETIRED")]
    _alembic(backend_dir, database_url, "upgrade", "head")
    assert asyncio.run(
        _values(
            database_url,
            "SELECT catalog_version FROM catalog_activation WHERE singleton_id = 1",
        )
    ) == [("roundtrip-v1",)]
    assert asyncio.run(
        _values(
            database_url,
            "SELECT release.catalog_version, release.content_hash, release.exercise_count, "
            "COUNT(exercise.id) FROM catalog_releases release "
            "LEFT JOIN catalog_release_exercises exercise "
            "ON exercise.catalog_version = release.catalog_version "
            "WHERE release.catalog_version = 'roundtrip-v1' "
            "GROUP BY release.catalog_version, release.content_hash, release.exercise_count",
        )
    ) == [("roundtrip-v1", "sha256:" + "1" * 64, 1, 1)]


@pytest.mark.integration
def test_catalog_release_migration_preserves_active_data_on_sqlite(tmp_path: Path) -> None:
    database_url = f"sqlite+aiosqlite:///{tmp_path / 'catalog-roundtrip.db'}"
    _assert_catalog_round_trip(Path(__file__).resolve().parents[1], database_url)


async def _recreate_postgres_database(database_url: str, *, drop_only: bool = False) -> None:
    url = make_url(database_url)
    database_name = f"{url.database}_migrations"
    assert re.fullmatch(r"[a-zA-Z0-9_]+", database_name)
    admin_engine = create_async_engine(url.set(database="postgres"), isolation_level="AUTOCOMMIT")
    async with admin_engine.connect() as connection:
        await connection.execute(text(f'DROP DATABASE IF EXISTS "{database_name}" WITH (FORCE)'))
        if not drop_only:
            await connection.execute(text(f'CREATE DATABASE "{database_name}"'))
    await admin_engine.dispose()


@pytest.mark.integration
def test_catalog_release_migration_preserves_active_data_on_postgresql() -> None:
    source_url = os.getenv("FITNESS_TEST_POSTGRES_URL")
    if not source_url:
        pytest.skip("FITNESS_TEST_POSTGRES_URL is required for PostgreSQL migration tests")
    parsed = make_url(source_url)
    migration_url = parsed.set(database=f"{parsed.database}_migrations").render_as_string(
        hide_password=False
    )
    asyncio.run(_recreate_postgres_database(source_url))
    try:
        _assert_catalog_round_trip(Path(__file__).resolve().parents[1], migration_url)
    finally:
        asyncio.run(_recreate_postgres_database(source_url, drop_only=True))


@pytest.mark.integration
def test_catalog_release_migration_handles_no_activation(tmp_path: Path) -> None:
    backend_dir = Path(__file__).resolve().parents[1]
    database_url = f"sqlite+aiosqlite:///{tmp_path / 'catalog-empty.db'}"
    _alembic(backend_dir, database_url, "upgrade", "head")
    _alembic(backend_dir, database_url, "downgrade", "f26c8d0e531a")
    assert asyncio.run(_values(database_url, "SELECT COUNT(*) FROM catalog_exercises")) == [(0,)]
    _alembic(backend_dir, database_url, "upgrade", "head")
    assert asyncio.run(_values(database_url, "SELECT catalog_version FROM catalog_activation")) == [
        (None,)
    ]


@pytest.mark.integration
def test_catalog_release_migration_preserves_empty_active_release(tmp_path: Path) -> None:
    backend_dir = Path(__file__).resolve().parents[1]
    database_url = f"sqlite+aiosqlite:///{tmp_path / 'catalog-empty-active.db'}"
    _alembic(backend_dir, database_url, "upgrade", "head")
    asyncio.run(_seed_empty(database_url))
    _alembic(backend_dir, database_url, "downgrade", "f26c8d0e531a")
    assert asyncio.run(_values(database_url, "SELECT COUNT(*) FROM catalog_exercises")) == [(0,)]
    assert asyncio.run(
        _values(
            database_url, "SELECT status FROM catalog_releases WHERE catalog_version='empty-v1'"
        )
    ) == [("PUBLISHED",)]
    _alembic(backend_dir, database_url, "upgrade", "head")
    assert asyncio.run(
        _values(
            database_url,
            "SELECT activation.catalog_version, release.exercise_count, COUNT(exercise.id) "
            "FROM catalog_activation activation "
            "JOIN catalog_releases release ON release.catalog_version=activation.catalog_version "
            "LEFT JOIN catalog_release_exercises exercise "
            "ON exercise.catalog_version=release.catalog_version "
            "GROUP BY activation.catalog_version, release.exercise_count",
        )
    ) == [("empty-v1", 0, 0)]
