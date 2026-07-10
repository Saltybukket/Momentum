# ADR-007: External systems behind provider ports

- Status: Accepted
- Date: 2026-07-10

## Context

Health, nutrition, wearable, payment, ad, notification, integrity and AI providers differ by availability, authorization, platform and commercial agreement. Some named providers have no permitted public integration path.

## Decision

Define normalized provider ports in the core/application boundary. Implement deterministic mocks for testability. Production adapters may use only official/authorized interfaces and must pass shared contract tests.

## Consequences

Domain logic survives vendor changes and unavailable credentials. Provider capability differences must be modeled explicitly rather than hidden. No reverse-engineered YAZIO/RENPHO API is allowed.
