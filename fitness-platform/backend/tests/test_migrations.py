import os
import subprocess
import sys
from pathlib import Path

import pytest
from sqlalchemy import create_engine, inspect


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
