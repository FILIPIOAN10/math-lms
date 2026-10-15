from functools import lru_cache

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """All configuration comes from environment variables (or `.env` locally)."""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # Optional here so the database-only commands run without a key; the LLM factory
    # will require it when it is actually used.
    openai_api_key: SecretStr | None = None

    chat_model: str = "gpt-6-luna"
    embedding_model: str = "text-embedding-3-small"

    openai_base_url: str = "https://api.openai.com/v1"
    reasoning_effort: str = "low"
    image_detail: str = "high"
    # USD per million tokens, only for the cost estimate printed by the tools (same as backend application.yml).
    price_in_per_million: float = 0.10
    price_out_per_million: float = 0.50

    # The LMS database (docker-compose.yml: Postgres + pgvector on host port 5433).
    database_url: str = "postgresql+psycopg://mathlms:mathlms_dev_password@localhost:5433/mathlms"

    @property
    def psycopg_dsn(self) -> str:
        """Plain libpq DSN, for code that talks to psycopg directly (no SQLAlchemy driver suffix)."""
        return self.database_url.replace("postgresql+psycopg://", "postgresql://", 1)


@lru_cache
def get_settings() -> Settings:
    return Settings()
