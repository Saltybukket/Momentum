SHELL := /bin/sh
.DEFAULT_GOAL := help

ifneq ($(wildcard $(HOME)/Android/Sdk),)
ANDROID_HOME ?= $(HOME)/Android/Sdk
ANDROID_SDK_ROOT ?= $(ANDROID_HOME)
export ANDROID_HOME ANDROID_SDK_ROOT
endif

.PHONY: help doctor doctor-connected setup dev backend backend-test backend-lint android-test android-build android-connected-test test lint migrate seed catalog-import idempotency-cleanup sync-operation-cleanup outbox-process clean openapi smoke source-archive
help:
	@printf '%s\n' "doctor doctor-connected setup dev backend backend-test backend-lint android-test android-build android-connected-test test lint migrate seed catalog-import idempotency-cleanup sync-operation-cleanup outbox-process clean openapi smoke source-archive"

doctor:
	./scripts/doctor.sh

doctor-connected:
	./scripts/doctor.sh --connected

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

android-connected-test:
	./scripts/android-connected-tests.sh

test: backend-test android-test

lint: backend-lint
	cd android && ./gradlew spotlessCheck detekt lintDebug

migrate:
	cd backend && uv run alembic upgrade head

seed:
	cd backend && uv run fitness-seed

catalog-import:
	cd backend && uv run python -m fitness_platform.catalog_import ../data/exercises/catalog-demo.json --report ../data/licenses/catalog-import-report.json

idempotency-cleanup:
	cd backend && uv run python -m fitness_platform.maintenance idempotency-cleanup

sync-operation-cleanup:
	cd backend && uv run python -m fitness_platform.maintenance sync-operation-cleanup

outbox-process:
	cd backend && uv run python -m fitness_platform.maintenance outbox-process

openapi:
	cd backend && uv run python ../scripts/export_openapi.py

smoke:
	cd backend && uv run python ../scripts/smoke_test.py

source-archive:
	python3 scripts/source_archive.py

clean:
	rm -rf backend/.venv backend/.pytest_cache backend/.mypy_cache backend/.ruff_cache backend/.coverage backend/htmlcov
	cd android && ./gradlew clean || true
