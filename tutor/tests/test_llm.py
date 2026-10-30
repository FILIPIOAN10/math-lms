"""Which client each provider gets, and the clear messages when its configuration is missing."""

import pytest
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_openai import ChatOpenAI

from tutor.config import Settings
from tutor.llm import chat_client, embeddings, gemini_access


def test_gemini_needs_a_project():
    with pytest.raises(RuntimeError, match="GEMINI_PROJECT"):
        embeddings(Settings(_env_file=None))


def test_gemini_needs_the_service_account_key():
    with pytest.raises(RuntimeError, match="GEMINI_CREDENTIALS_FILE"):
        gemini_access(Settings(_env_file=None, gemini_project="p"))


def test_gemini_is_the_default_and_openai_a_setting(monkeypatch):
    monkeypatch.setattr("tutor.llm.gemini_access", lambda _settings: {"project": "p", "location": "global"})

    assert isinstance(chat_client(Settings(_env_file=None), 500), ChatGoogleGenerativeAI)
    assert isinstance(
        chat_client(Settings(_env_file=None, llm_provider="openai", openai_api_key="sk-test", chat_model="m"), 500),
        ChatOpenAI)
