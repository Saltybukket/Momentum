# ADR-003: Android MVVM with unidirectional UI state

- Status: Accepted
- Date: 2026-07-10

## Context

Compose benefits from immutable observable state. A full reducer/MVI framework would add ceremony for the initial slice, while ad-hoc mutable screen state would become hard to test.

## Decision

Use MVVM with one immutable `StateFlow` per feature ViewModel and explicit actions/use cases. Navigation passes IDs. Framework-free domain contracts remain outside ViewModels.

## Consequences

State and error/loading behavior are testable without adding an MVI dependency. Complex future workflows may introduce reducers internally while preserving the state/action interface.
