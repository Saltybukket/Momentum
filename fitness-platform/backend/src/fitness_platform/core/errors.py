from dataclasses import dataclass, field
from typing import Any


@dataclass(slots=True)
class AppError(Exception):
    code: str
    message: str
    status_code: int
    details: list[dict[str, Any]] = field(default_factory=list)


class NotFoundError(AppError):
    def __init__(self, message: str = "Resource not found.") -> None:
        super().__init__("NOT_FOUND", message, 404)


class ConflictError(AppError):
    def __init__(self, message: str = "The request conflicts with current state.") -> None:
        super().__init__("CONFLICT", message, 409)


class UnauthorizedError(AppError):
    def __init__(self, message: str = "Authentication is required.") -> None:
        super().__init__("UNAUTHORIZED", message, 401)


class ValidationAppError(AppError):
    def __init__(self, message: str, details: list[dict[str, Any]] | None = None) -> None:
        super().__init__("VALIDATION_ERROR", message, 422, details or [])
