"""The model clients, built from settings in one place so the provider can change without touching the callers.

Gemini runs on Google Cloud (Vertex AI), signed in as the same service account as the backend's AI import, so it is
billed to the same project. OpenAI stays available with LLM_PROVIDER=openai.
"""

from google.oauth2 import service_account
from langchain_core.embeddings import Embeddings
from langchain_core.language_models import BaseChatModel
from langchain_google_genai import ChatGoogleGenerativeAI, GoogleGenerativeAIEmbeddings
from langchain_openai import ChatOpenAI, OpenAIEmbeddings

from tutor.config import Settings

CLOUD_SCOPE = "https://www.googleapis.com/auth/cloud-platform"


def using_openai(settings: Settings) -> bool:
    return settings.llm_provider == "openai"


def gemini_access(settings: Settings) -> dict:
    """What every Gemini client needs: the project, its location and the service account's credentials."""
    if not settings.gemini_project:
        raise RuntimeError("Lipsește GEMINI_PROJECT (pune-l în tutor/.env).")
    if not settings.gemini_credentials_file:
        raise RuntimeError("Lipsește GEMINI_CREDENTIALS_FILE: calea către cheia JSON a contului de serviciu.")
    credentials = service_account.Credentials.from_service_account_file(
        settings.gemini_credentials_file, scopes=[CLOUD_SCOPE])
    return {"project": settings.gemini_project, "location": settings.gemini_location, "credentials": credentials}


def embeddings(settings: Settings) -> Embeddings:
    if using_openai(settings):
        if settings.openai_api_key is None:
            raise RuntimeError("Lipsește OPENAI_API_KEY (pune-l în tutor/.env).")
        return OpenAIEmbeddings(
            model=settings.embedding_model,
            api_key=settings.openai_api_key,
            base_url=settings.openai_base_url,
        )
    return GoogleGenerativeAIEmbeddings(
        model=settings.embedding_model,
        vertexai=True,
        output_dimensionality=settings.embedding_dimensions,  # the size of the tutor_chunks.embedding column
        **gemini_access(settings),
    )


def chat_options(settings: Settings, max_tokens: int) -> dict:
    """Arguments for the OpenAI client: the Responses API, nothing stored at the provider."""
    if settings.openai_api_key is None:
        raise RuntimeError("Lipsește OPENAI_API_KEY (pune-l în tutor/.env).")
    options: dict = {
        "model": settings.chat_model,
        "api_key": settings.openai_api_key,
        "base_url": settings.openai_base_url,
        "use_responses_api": True,
        "store": False,
        "max_tokens": max_tokens,
    }
    if settings.reasoning_effort:
        options["reasoning"] = {"effort": settings.reasoning_effort}
    if settings.chat_temperature is not None:
        options["temperature"] = settings.chat_temperature
    return options


def chat_client(settings: Settings, max_tokens: int) -> BaseChatModel:
    if using_openai(settings):
        return ChatOpenAI(**chat_options(settings, max_tokens))
    options: dict = {}
    if settings.chat_temperature is not None:
        options["temperature"] = settings.chat_temperature
    return ChatGoogleGenerativeAI(
        model=settings.chat_model, vertexai=True, max_output_tokens=max_tokens, **gemini_access(settings), **options)
