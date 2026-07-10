from datetime import UTC, date, datetime, timedelta
from uuid import uuid4

import pytest

from fitness_platform.providers.mocks import (
    MockActivityProvider,
    MockAuthenticationProvider,
    MockBodyMeasurementProvider,
    MockHealthDataProvider,
    MockNutritionProvider,
)


@pytest.mark.asyncio
@pytest.mark.contract
async def test_mock_provider_contracts_are_deterministic() -> None:
    user_id = uuid4()
    day = date(2026, 1, 1)
    start = datetime(2026, 1, 1, tzinfo=UTC)
    end = start + timedelta(days=1)

    health = MockHealthDataProvider()
    nutrition = MockNutritionProvider()
    activity = MockActivityProvider()
    body = MockBodyMeasurementProvider()
    auth = MockAuthenticationProvider()

    assert await health.read_samples(user_id, start, end) == await health.read_samples(
        user_id, start, end
    )
    assert (await nutrition.read_day(user_id, day)).calories_kcal == 2100.0
    assert (await activity.read_day(user_id, day)).steps == 8000
    assert (await body.read_latest(user_id)) is not None
    assert (await auth.verify("credential")).provider == "mock"
