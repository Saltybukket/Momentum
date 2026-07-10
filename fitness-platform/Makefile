SHELL := /bin/sh
.DEFAULT_GOAL := help

.PHONY: help setup dev backend backend-test backend-lint android-test android-build test lint migrate seed clean openapi smoke
help:
	@printf '%s\n' "setup dev backend backend-test backend-lint android-test android-build test lint migrate seed clean openapi smoke"

setup:
	@test -f .env || cp .env.example .env
	cd backend && uv sync --extra dev --frozen

dev:
	docker compose up --build

backend:
	cd backend && uv run uvicorn fitness_platform.main:app --reload --host 0.0.0.0 --port 8000

backend-test:
	cd backend && uv run pytest

backend-lint:
	cd backend && uv run ruff format --check . ../scripts && uv run ruff check . ../scripts && uv run mypy src

android-test:
	cd android && ./gradlew test

android-build:
	cd android && ./gradlew lintDebug assembleDebug

test: backend-test android-test

lint: backend-lint
	cd android && ./gradlew spotlessCheck detekt lintDebug

migrate:
	cd backend && uv run alembic upgrade head

seed:
	cd backend && uv run fitness-seed

openapi:
	cd backend && uv run python ../scripts/export_openapi.py

smoke:
	python scripts/smoke_test.py

clean:
	rm -rf backend/.venv backend/.pytest_cache backend/.mypy_cache backend/.ruff_cache backend/.coverage backend/htmlcov
	cd android && ./gradlew clean || true
