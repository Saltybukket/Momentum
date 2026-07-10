# E2E foundation

The target flow is:

`local guest profile -> custom exercise -> workout -> complete -> outbox -> backend sync -> backend verification`.

Current automation is split deliberately:

1. Android Room/repository/instrumentation tests validate the local half and outbox creation.
2. Backend API tests validate idempotent persistence and `WorkoutCompleted` delivery.
3. `scripts/smoke_test.py` validates a running Docker backend through the public HTTP boundary.

A device-level test spanning emulator and Docker is the next testing slice, not falsely reported as complete here.
