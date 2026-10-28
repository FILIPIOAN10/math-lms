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
    # None = the model's own default: reasoning models reject `temperature`, so stability comes from low reasoning
    # effort and a strict output schema. Set it only for a model that accepts it.
    chat_temperature: float | None = None
    help_max_output_tokens: int = 3_000

    # Help chain: how many textbook passages go into the prompt, and the nearest-passage distance above which the
    # question is declared "not in the book". 0.48 sits in the gap between the farthest covered question (0.4646) and
    # the second-nearest uncovered one (0.5057); the one uncovered question closer than that (0.4048) is stopped by the
    # model's own `covered` flag (eval/help-report.md). Do not round the measured boundary down: 0.46 refused a covered one.
    retrieval_k: int = 4
    max_distance: float = 0.48

    # Conversation: how many of the last messages (student + tutor) are kept, and how long a message can be and still
    # be treated as a possible follow-up that needs the history to make sense (a longer one is an exercise of its own).
    history_messages: int = 6
    condense_max_chars: int = 200
    image_detail: str = "high"
    # USD per million tokens, only for the cost estimate printed by the tools (same as backend application.yml).
    price_in_per_million: float = 0.10
    price_out_per_million: float = 0.50

    # Where the backend keeps the photos uploaded on the chapter pages (its EN_CHAPTER_IMAGES_DIR).
    en_chapter_images_dir: str = "../backend/uploads/en-chapters"

    # The LMS database (docker-compose.yml: Postgres + pgvector on host port 5433).
    database_url: str = "postgresql+psycopg://mathlms:mathlms_dev_password@localhost:5433/mathlms"

    @property
    def psycopg_dsn(self) -> str:
        """Plain libpq DSN, for code that talks to psycopg directly (no SQLAlchemy driver suffix)."""
        return self.database_url.replace("postgresql+psycopg://", "postgresql://", 1)


@lru_cache
def get_settings() -> Settings:
    return Settings()
