import os
import subprocess
import sys
from pathlib import Path

import pytest
from sqlalchemy import create_engine, inspect, text


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
def test_idempotency_migration_hashes_existing_keys(tmp_path: Path) -> None:
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
                "key": "raw-client-key",
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
    assert stored != "raw-client-key"
    assert len(stored) == 64
