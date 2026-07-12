from dataclasses import dataclass
from typing import cast

from fitness_platform.application.idempotency import IdempotencyService
from fitness_platform.application.outbox import OutboxProcessor
from fitness_platform.application.services import (
    CatalogService,
    ExerciseService,
    GuestService,
    ProfileService,
    SyncService,
    WorkoutService,
)
from fitness_platform.core.clock import SystemClock
from fitness_platform.core.config import Settings
from fitness_platform.core.database import Database
from fitness_platform.core.ids import RandomUuidProvider
from fitness_platform.core.rate_limit import InMemorySlidingWindowRateLimiter
from fitness_platform.core.redis import RedisClient
from fitness_platform.domain.events import InProcessEventDispatcher
from fitness_platform.domain.ports import UnitOfWork
from fitness_platform.infrastructure.uow import SqlAlchemyUnitOfWork


@dataclass(slots=True)
class AppContainer:
    settings: Settings
    database: Database
    redis: RedisClient
    events: InProcessEventDispatcher
    rate_limiter: InMemorySlidingWindowRateLimiter
    guests: GuestService
    profiles: ProfileService
    exercises: ExerciseService
    catalog: CatalogService
    workouts: WorkoutService
    sync: SyncService
    idempotency: IdempotencyService
    outbox_processor: OutboxProcessor

    @classmethod
    def build(cls, settings: Settings, database: Database | None = None) -> "AppContainer":
        db = database or Database(settings)
        redis = RedisClient(settings)
        clock = SystemClock()
        ids = RandomUuidProvider()
        events = InProcessEventDispatcher()

        def uow_factory() -> UnitOfWork:
            return cast(UnitOfWork, SqlAlchemyUnitOfWork(db.session_factory))

        workout_service = WorkoutService(
            uow_factory=uow_factory,
            clock=clock,
            ids=ids,
            event_dispatcher=events,
        )
        container = cls(
            settings=settings,
            database=db,
            redis=redis,
            events=events,
            rate_limiter=InMemorySlidingWindowRateLimiter(),
            guests=GuestService(
                uow_factory=uow_factory,
                clock=clock,
                ids=ids,
                event_dispatcher=events,
                token_pepper=settings.guest_token_pepper,
                token_ttl_hours=settings.guest_token_ttl_hours,
            ),
            profiles=ProfileService(uow_factory=uow_factory, clock=clock),
            exercises=ExerciseService(
                uow_factory=uow_factory,
                clock=clock,
                ids=ids,
                event_dispatcher=events,
            ),
            catalog=CatalogService(uow_factory=uow_factory),
            workouts=workout_service,
            sync=SyncService(
                uow_factory=uow_factory, clock=clock, ids=ids, workouts=workout_service
            ),
            idempotency=IdempotencyService(uow_factory, clock, events),
            outbox_processor=OutboxProcessor(uow_factory, clock, ids, events),
        )
        return container
