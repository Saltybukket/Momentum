from functools import lru_cache
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration loaded from environment variables.

    Secrets are never assigned production defaults. The development guest-token secret is only
    used to derive local token hashes and must be replaced outside local development.
    """

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="FITNESS_",
        case_sensitive=False,
        extra="ignore",
    )

    environment: Literal["local", "test", "staging", "production"] = "local"
    app_name: str = "Fitness Platform API"
    api_prefix: str = "/api/v1"
    database_url: str = "postgresql+asyncpg://fitness:fitness@postgres:5432/fitness"
    redis_url: str = "redis://redis:6379/0"
    log_level: str = "INFO"
    cors_origins: list[str] = Field(default_factory=lambda: ["http://localhost:3000"])
    guest_token_ttl_hours: int = 24 * 30
    guest_token_pepper: str = "local-development-only-change-me"
    rate_limit_guest_sessions_per_minute: int = 20
    enable_docs: bool = True

    @property
    def is_production(self) -> bool:
        return self.environment == "production"

    def validate_security_posture(self) -> None:
        if self.is_production and self.guest_token_pepper == "local-development-only-change-me":
            raise RuntimeError("FITNESS_GUEST_TOKEN_PEPPER must be set in production")


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    settings = Settings()
    settings.validate_security_posture()
    return settings
