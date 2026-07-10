from dataclasses import dataclass
from datetime import date, datetime
from typing import Protocol
from uuid import UUID


@dataclass(frozen=True, slots=True)
class HealthSample:
    type: str
    value: float
    unit: str
    measured_at: datetime
    source: str


@dataclass(frozen=True, slots=True)
class NutritionDay:
    day: date
    calories_kcal: float
    protein_g: float
    carbohydrates_g: float
    fat_g: float
    water_ml: float
    source: str


@dataclass(frozen=True, slots=True)
class ActivitySummary:
    day: date
    steps: int
    active_minutes: int
    distance_m: float
    source: str


@dataclass(frozen=True, slots=True)
class BodyMeasurement:
    measured_at: datetime
    weight_kg: float
    body_fat_percent: float | None
    source: str
    provenance: str


@dataclass(frozen=True, slots=True)
class AuthIdentity:
    provider: str
    subject: str
    email: str | None


class HealthDataProvider(Protocol):
    async def read_samples(
        self, user_id: UUID, start: datetime, end: datetime
    ) -> list[HealthSample]: ...


class NutritionProvider(Protocol):
    async def read_day(self, user_id: UUID, day: date) -> NutritionDay: ...


class ActivityProvider(Protocol):
    async def read_day(self, user_id: UUID, day: date) -> ActivitySummary: ...


class BodyMeasurementProvider(Protocol):
    async def read_latest(self, user_id: UUID) -> BodyMeasurement | None: ...


class AuthenticationProvider(Protocol):
    async def verify(self, credential: str) -> AuthIdentity: ...


class PaymentProvider(Protocol):
    async def verify_purchase(self, purchase_token: str, product_id: str) -> bool: ...


class AdvertisementProvider(Protocol):
    async def verify_reward(self, reward_token: str) -> bool: ...


class NotificationProvider(Protocol):
    async def send(self, user_id: UUID, title: str, body: str) -> str: ...


class IntegrityProvider(Protocol):
    async def verify(self, token: str, request_hash: str) -> bool: ...


class AiProvider(Protocol):
    async def complete(self, prompt: str, max_tokens: int) -> str: ...
