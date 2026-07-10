param([ValidateSet('setup','dev','backend','backend-test','backend-lint','android-test','android-build','test','lint','migrate','seed','smoke')][string]$Task = 'setup')
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
switch ($Task) {
  'setup' { if (-not (Test-Path .env)) { Copy-Item .env.example .env }; Push-Location backend; uv sync --extra dev --frozen; Pop-Location }
  'dev' { docker compose up --build }
  'backend' { Push-Location backend; uv run uvicorn fitness_platform.main:app --reload --host 0.0.0.0 --port 8000; Pop-Location }
  'backend-test' { Push-Location backend; uv run pytest; Pop-Location }
  'backend-lint' { Push-Location backend; uv run ruff format --check . ../scripts; uv run ruff check . ../scripts; uv run mypy src; Pop-Location }
  'android-test' { Push-Location android; .\gradlew.bat test; Pop-Location }
  'android-build' { Push-Location android; .\gradlew.bat lintDebug assembleDebug; Pop-Location }
  'test' { & $PSCommandPath backend-test; & $PSCommandPath android-test }
  'lint' { & $PSCommandPath backend-lint; Push-Location android; .\gradlew.bat spotlessCheck detekt lintDebug; Pop-Location }
  'migrate' { Push-Location backend; uv run alembic upgrade head; Pop-Location }
  'seed' { Push-Location backend; uv run fitness-seed; Pop-Location }
  'smoke' { python scripts/smoke_test.py }
}
