from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from fitness_platform.core.errors import AppError


def _request_id(request: Request) -> str:
    return getattr(request.state, "request_id", request.headers.get("X-Request-ID", "unknown"))


def _payload(
    *, code: str, message: str, details: list[dict[str, Any]], request_id: str
) -> dict[str, Any]:
    return {
        "error": {
            "code": code,
            "message": message,
            "details": details,
            "request_id": request_id,
        }
    }


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def handle_app_error(request: Request, exc: AppError) -> JSONResponse:
        return JSONResponse(
            status_code=exc.status_code,
            content=_payload(
                code=exc.code,
                message=exc.message,
                details=exc.details,
                request_id=_request_id(request),
            ),
        )

    @app.exception_handler(RequestValidationError)
    async def handle_validation_error(
        request: Request, exc: RequestValidationError
    ) -> JSONResponse:
        details = [
            {
                "location": [str(part) for part in error["loc"]],
                "message": error["msg"],
                "type": error["type"],
            }
            for error in exc.errors()
        ]
        return JSONResponse(
            status_code=422,
            content=_payload(
                code="VALIDATION_ERROR",
                message="The request contains invalid data.",
                details=details,
                request_id=_request_id(request),
            ),
        )
