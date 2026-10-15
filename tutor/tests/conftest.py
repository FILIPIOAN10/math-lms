"""A throw-away database built from the real Flyway migrations (needs the dev Postgres up; skipped otherwise)."""

import pathlib
import uuid

import psycopg
import pytest
from psycopg.conninfo import make_conninfo

from tutor.config import get_settings

MIGRATIONS = pathlib.Path(__file__).resolve().parents[2] / "backend/src/main/resources/db/migration"
VERSIONS = [
    "V33__en_chapters.sql",
    "V36__tutor_chunks.sql",
    "V37__tutor_chunk_source.sql",
    "V38__en_chapter_sections.sql",
]


@pytest.fixture
def conn():
    settings = get_settings()
    try:
        admin = psycopg.connect(settings.psycopg_dsn, autocommit=True, connect_timeout=3)
    except psycopg.OperationalError:
        pytest.skip("Postgres de dezvoltare nu rulează")
    name = f"tutor_test_{uuid.uuid4().hex[:8]}"
    admin.execute(f'CREATE DATABASE "{name}"')
    connection = psycopg.connect(make_conninfo(settings.psycopg_dsn, dbname=name), autocommit=True)
    try:
        for version in VERSIONS:
            connection.execute((MIGRATIONS / version).read_text(encoding="utf-8"))
        # V33 seeds A1..G9; the tests add the pages of the book they talk about.
        connection.execute("DELETE FROM en_chapters")
        connection.execute(
            "INSERT INTO en_chapters (code, title, area, position) VALUES "
            "('AI', 'Mulțimea numerelor naturale', 'ALGEBRA', 1),"
            "('AIS2', 'Adunarea și scăderea', 'ALGEBRA', 2),"
            "('AIS2P2', 'Adunarea și scăderea: Scăderea', 'ALGEBRA', 3)")
        yield connection
    finally:
        connection.close()
        admin.execute(f'DROP DATABASE "{name}" WITH (FORCE)')
        admin.close()
