# ADR-006: FastAPI, Pydantic and SQLAlchemy 2

- Status: Accepted
- Date: 2026-07-10

## Context

The required backend needs typed HTTP contracts, OpenAPI, async-capable persistence, migrations and strong Python test tooling.

## Decision

Use FastAPI for presentation, Pydantic for transport/configuration, SQLAlchemy 2 repository adapters, Alembic migrations, PostgreSQL as canonical storage and Redis for non-authoritative operational concerns.

## Consequences

The stack is mature and well-supported. Domain models remain separate from Pydantic/ORM classes to prevent framework coupling. Async style must stay consistent and transaction ownership explicit.
