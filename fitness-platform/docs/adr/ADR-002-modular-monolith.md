# ADR-002: Begin with a modular monolith

- Status: Accepted
- Date: 2026-07-10

## Context

The product has many domains but no demonstrated independent scaling or organizational boundaries. Premature services would multiply networking, consistency, deployment and observability work.

## Decision

Deploy one FastAPI application and PostgreSQL database. Express module ownership through an executable module catalog, layered code, repository/application boundaries and domain events. Prevent dependency cycles in tests.

## Consequences

Transactions and local development remain simple. Modules must not bypass ownership by reading foreign tables directly. Extraction is considered only with evidence for independent scale/availability/team ownership.
