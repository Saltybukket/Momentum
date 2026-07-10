from datetime import UTC, date, datetime
from uuid import UUID

from fitness_platform.providers.ports import (
    ActivitySummary,
    AuthIdentity,
    BodyMeasurement,
    HealthSample,
    NutritionDay,
)


class MockHealthDataProvider:
    async def read_samples(
        self, user_id: UUID, start: datetime, end: datetime
    ) -> list[HealthSample]:
        if end <= start:
            return []
        return [
            HealthSample(
                type="resting_heart_rate",
                value=60.0,
                unit="bpm",
                measured_at=start + (end - start) / 2,
                source="mock-health",
            )
        ]


class MockNutritionProvider:
    async def read_day(self, user_id: UUID, day: date) -> NutritionDay:
        return NutritionDay(
            day=day,
            calories_kcal=2100.0,
            protein_g=140.0,
            carbohydrates_g=230.0,
            fat_g=70.0,
            water_ml=2200.0,
            source="mock-nutrition",
        )


class MockActivityProvider:
    async def read_day(self, user_id: UUID, day: date) -> ActivitySummary:
        return ActivitySummary(
            day=day,
            steps=8000,
            active_minutes=45,
            distance_m=6100.0,
            source="mock-activity",
        )


class MockBodyMeasurementProvider:
    async def read_latest(self, user_id: UUID) -> BodyMeasurement | None:
        return BodyMeasurement(
            measured_at=datetime(2026, 1, 1, 8, tzinfo=UTC),
            weight_kg=75.0,
            body_fat_percent=18.0,
            source="mock-body-measurement",
            provenance="device-calculated",
        )


class MockAuthenticationProvider:
    async def verify(self, credential: str) -> AuthIdentity:
        if not credential:
            raise ValueError("credential must not be empty")
        return AuthIdentity(provider="mock", subject="mock-user", email="mock@example.invalid")


class MockPaymentProvider:
    async def verify_purchase(self, purchase_token: str, product_id: str) -> bool:
        return purchase_token == "valid-test-purchase" and product_id.startswith("test.")


class MockAdvertisementProvider:
    async def verify_reward(self, reward_token: str) -> bool:
        return reward_token == "valid-test-reward"


class MockNotificationProvider:
    async def send(self, user_id: UUID, title: str, body: str) -> str:
        return f"mock-notification:{user_id}:{len(title) + len(body)}"


class MockIntegrityProvider:
    async def verify(self, token: str, request_hash: str) -> bool:
        return token == f"mock-integrity:{request_hash}"


class MockAiProvider:
    async def complete(self, prompt: str, max_tokens: int) -> str:
        return f"Mock response ({min(max_tokens, 64)} token budget): {prompt[:80]}"
