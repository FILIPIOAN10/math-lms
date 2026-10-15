from tutor.config import Settings


def test_defaults_point_to_the_lms_database_from_docker_compose():
    settings = Settings(_env_file=None)

    assert settings.database_url.endswith("localhost:5433/mathlms")
    assert settings.embedding_model == "text-embedding-3-small"
    assert settings.openai_api_key is None


def test_environment_overrides_defaults(monkeypatch):
    monkeypatch.setenv("CHAT_MODEL", "gpt-4o")
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test")

    settings = Settings(_env_file=None)

    assert settings.chat_model == "gpt-4o"
    assert settings.openai_api_key.get_secret_value() == "sk-test"
    # the key must not leak through repr / logs
    assert "sk-test" not in repr(settings)


def test_psycopg_dsn_drops_the_sqlalchemy_driver_suffix():
    settings = Settings(_env_file=None, database_url="postgresql+psycopg://u:p@h:1/db")

    assert settings.psycopg_dsn == "postgresql://u:p@h:1/db"
