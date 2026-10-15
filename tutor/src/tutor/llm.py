"""The model clients, built from settings in one place so the provider can change without touching the callers."""

from langchain_core.embeddings import Embeddings
from langchain_openai import OpenAIEmbeddings

from tutor.config import Settings


def embeddings(settings: Settings) -> Embeddings:
    if settings.openai_api_key is None:
        raise RuntimeError("Lipsește OPENAI_API_KEY (pune-l în tutor/.env).")
    return OpenAIEmbeddings(
        model=settings.embedding_model,
        api_key=settings.openai_api_key,
        base_url=settings.openai_base_url,
    )
