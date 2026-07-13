from types import TracebackType

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from fitness_platform.infrastructure.catalog_releases import SqlAlchemyReleaseCatalogRepository
from fitness_platform.infrastructure.repositories import (
    SqlAlchemyExerciseRepository,
    SqlAlchemyGuestSessionRepository,
    SqlAlchemyIdempotencyRepository,
    SqlAlchemyOutboxRepository,
    SqlAlchemyProcessedSyncOperationRepository,
    SqlAlchemyProfileRepository,
    SqlAlchemyUserRepository,
    SqlAlchemyWorkoutRepository,
)


class SqlAlchemyUnitOfWork:
    def __init__(self, session_factory: async_sessionmaker[AsyncSession]) -> None:
        self._session_factory = session_factory
        self._session: AsyncSession | None = None

    async def __aenter__(self) -> "SqlAlchemyUnitOfWork":
        self._session = self._session_factory()
        await self._session.begin()
        self.users = SqlAlchemyUserRepository(self._session)
        self.guest_sessions = SqlAlchemyGuestSessionRepository(self._session)
        self.profiles = SqlAlchemyProfileRepository(self._session)
        self.exercises = SqlAlchemyExerciseRepository(self._session)
        self.catalog = SqlAlchemyReleaseCatalogRepository(self._session)
        self.workouts = SqlAlchemyWorkoutRepository(self._session)
        self.outbox = SqlAlchemyOutboxRepository(self._session)
        self.idempotency = SqlAlchemyIdempotencyRepository(self._session)
        self.processed_sync_operations = SqlAlchemyProcessedSyncOperationRepository(self._session)
        return self

    async def __aexit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        if self._session is None:
            return
        if exc is not None:
            await self._session.rollback()
        await self._session.close()

    async def commit(self) -> None:
        if self._session is None:
            raise RuntimeError("UnitOfWork is not active")
        await self._session.commit()

    async def rollback(self) -> None:
        if self._session is None:
            raise RuntimeError("UnitOfWork is not active")
        await self._session.rollback()
