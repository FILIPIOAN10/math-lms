"""„Dă-mi una asemănătoare": the questions of the bank and the worked examples get one embedding each (similar_index,
V71); a second run embeds only what changed, and the HTTP service runs it for the backend's nightly job."""

import pathlib

import pytest
from fastapi.testclient import TestClient
from langchain_core.embeddings import DeterministicFakeEmbedding
from pydantic import SecretStr

from conftest import MIGRATIONS
from tutor.api import create_app, similar_indexer
from tutor.config import Settings
from tutor.similar_index import IndexReport, index_similar


class CountingEmbeddings(DeterministicFakeEmbedding):
    """The fake model, counting the texts it was asked to embed (each one would be paid)."""

    embedded: list[str] = []

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        self.embedded.extend(texts)
        return super().embed_documents(texts)


@pytest.fixture
def bank(conn):
    # The real quiz_items has many more columns; the index reads only these two.
    conn.execute("CREATE TABLE quiz_items (id BIGINT PRIMARY KEY, statement TEXT NOT NULL)")
    conn.execute((MIGRATIONS / "V71__similar_index.sql").read_text(encoding="utf-8"))
    chapter = conn.execute("SELECT id FROM en_chapters WHERE code = 'AI'").fetchone()[0]
    conn.execute("INSERT INTO quiz_items (id, statement) VALUES (1, 'Calculează $2 + 3 \cdot 4$.'), "
                 "(2, 'Aflați $x$ din $2x = 6$.'), (3, '   ')")
    conn.execute("INSERT INTO en_chapter_examples (id, chapter_id, position, statement, solution) VALUES "
                 "(10, %s, 1, 'Ordinea operațiilor: $5 + 2 \cdot 3$.', '$5 + 6 = 11$')", (chapter,))
    return conn


def stored(conn) -> list[tuple]:
    rows = conn.execute("SELECT item_id, example_id, embedded_with FROM similar_index").fetchall()
    return sorted(rows, key=lambda r: (r[0] or 0, r[1] or 0))


def embeddings() -> CountingEmbeddings:
    model = CountingEmbeddings(size=1536)
    model.embedded = []
    return model


def test_every_question_and_example_gets_one_embedding_of_its_statement(bank):
    model = embeddings()

    report = index_similar(bank, model, "gemini-embedding-001")

    assert report == IndexReport(inserted=3, unchanged=0, removed=0)
    assert stored(bank) == [(None, 10, "gemini-embedding-001"), (1, None, "gemini-embedding-001"),
                            (2, None, "gemini-embedding-001")]
    assert "Ordinea operațiilor: $5 + 2 \cdot 3$." in model.embedded
    assert not any("11" in text for text in model.embedded)  # the statement only, never the solution
    assert "   " not in model.embedded  # a blank statement has nothing to compare


def test_a_second_run_embeds_only_what_changed(bank):
    index_similar(bank, embeddings(), "gemini-embedding-001")
    bank.execute("UPDATE quiz_items SET statement = 'Aflați $x$ din $3x = 9$.' WHERE id = 2")
    bank.execute("DELETE FROM quiz_items WHERE id = 1")
    model = embeddings()

    report = index_similar(bank, model, "gemini-embedding-001")

    assert model.embedded == ["Aflați $x$ din $3x = 9$."]
    assert report == IndexReport(inserted=1, unchanged=1, removed=1)
    assert stored(bank) == [(None, 10, "gemini-embedding-001"), (2, None, "gemini-embedding-001")]


def test_another_embedding_model_reindexes_everything(bank):
    index_similar(bank, embeddings(), "old-model")

    report = index_similar(bank, embeddings(), "gemini-embedding-001")

    assert report.inserted == 3 and report.removed == 3
    assert {row[2] for row in stored(bank)} == {"gemini-embedding-001"}


def test_a_dry_run_changes_nothing(bank):
    model = embeddings()

    report = index_similar(bank, model, "gemini-embedding-001", dry_run=True)

    assert report.inserted == 3 and stored(bank) == [] and model.embedded == []


def test_the_service_runs_the_indexing_for_the_backend():
    settings = Settings(_env_file=None, service_token=SecretStr("tok"))
    app = create_app(settings)
    app.dependency_overrides[similar_indexer] = lambda: (lambda: IndexReport(inserted=4, unchanged=7, removed=1))
    client = TestClient(app)

    response = client.post("/similar-index", headers={"X-Tutor-Token": "tok"})

    assert response.status_code == 200
    assert response.json() == {"inserted": 4, "unchanged": 7, "removed": 1}
    assert client.post("/similar-index").status_code == 401


def test_the_migration_is_where_the_test_reads_it():
    assert pathlib.Path(MIGRATIONS / "V71__similar_index.sql").exists()
