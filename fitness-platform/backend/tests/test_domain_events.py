from uuid import uuid4

import pytest

from fitness_platform.domain.events import InProcessEventDispatcher, WorkoutCompleted


@pytest.mark.asyncio
async def test_event_dispatcher_processes_event_id_once() -> None:
    dispatcher = InProcessEventDispatcher()
    calls: list[str] = []

    async def handler(event: WorkoutCompleted) -> None:
        calls.append(str(event.workout_id))

    dispatcher.register(WorkoutCompleted, handler)
    event = WorkoutCompleted(workout_id=uuid4(), user_id=uuid4())

    assert await dispatcher.dispatch(event) is True
    assert await dispatcher.dispatch(event) is False
    assert calls == [str(event.workout_id)]
