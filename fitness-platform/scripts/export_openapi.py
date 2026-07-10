from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend" / "src"))
from fitness_platform.core.config import Settings  # noqa: E402
from fitness_platform.main import create_app  # noqa: E402

app = create_app(
    Settings(
        environment="test",
        database_url="sqlite+aiosqlite:///:memory:",
        redis_url="redis://localhost:6379/15",
    )
)
output = ROOT / "shared" / "openapi.json"
output.write_text(json.dumps(app.openapi(), indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(output)
