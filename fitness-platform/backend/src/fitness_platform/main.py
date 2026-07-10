from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from fitness_platform.container import AppContainer
from fitness_platform.core.config import Settings, get_settings
from fitness_platform.core.exception_handlers import register_exception_handlers
from fitness_platform.core.logging import configure_logging
from fitness_platform.core.middleware import RequestContextMiddleware
from fitness_platform.presentation.api import router


def create_app(settings: Settings | None = None, container: AppContainer | None = None) -> FastAPI:
    runtime_settings = settings or get_settings()
    configure_logging(runtime_settings.log_level)
    runtime_container = container or AppContainer.build(runtime_settings)

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        app.state.container = runtime_container
        yield
        await runtime_container.redis.close()
        await runtime_container.database.dispose()

    app = FastAPI(
        title=runtime_settings.app_name,
        version="0.1.0",
        docs_url="/docs" if runtime_settings.enable_docs else None,
        redoc_url="/redoc" if runtime_settings.enable_docs else None,
        lifespan=lifespan,
    )
    app.state.container = runtime_container
    app.add_middleware(RequestContextMiddleware)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=runtime_settings.cors_origins,
        allow_credentials=False,
        allow_methods=["GET", "POST", "PUT", "DELETE"],
        allow_headers=["Authorization", "Content-Type", "Idempotency-Key", "X-Request-ID"],
    )
    register_exception_handlers(app)
    app.include_router(router)
    return app


app = create_app()


def run() -> None:
    uvicorn.run("fitness_platform.main:app", host="0.0.0.0", port=8000, reload=False)


if __name__ == "__main__":
    run()
