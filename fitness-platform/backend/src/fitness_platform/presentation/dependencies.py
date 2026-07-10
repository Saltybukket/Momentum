from datetime import UTC, datetime
from typing import Annotated, cast
from uuid import UUID

from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from fitness_platform.container import AppContainer
from fitness_platform.core.errors import UnauthorizedError
from fitness_platform.core.security import hash_token
from fitness_platform.infrastructure.uow import SqlAlchemyUnitOfWork

bearer = HTTPBearer(auto_error=False)


def get_container(request: Request) -> AppContainer:
    return cast(AppContainer, request.app.state.container)


async def get_current_user_id(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)],
    container: Annotated[AppContainer, Depends(get_container)],
) -> UUID:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise UnauthorizedError()
    token_hash = hash_token(credentials.credentials, container.settings.guest_token_pepper)
    async with SqlAlchemyUnitOfWork(container.database.session_factory) as uow:
        guest_session = await uow.guest_sessions.find_active_by_token_hash(
            token_hash, datetime.now(UTC)
        )
    if guest_session is None:
        raise UnauthorizedError("Guest token is invalid or expired.")
    return guest_session.user_id


CurrentUserId = Annotated[UUID, Depends(get_current_user_id)]
ContainerDep = Annotated[AppContainer, Depends(get_container)]
