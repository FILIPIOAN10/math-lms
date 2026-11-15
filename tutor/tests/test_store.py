"""Stores chunks in a throw-away database built from the real Flyway migrations (see conftest.py)."""

import pytest
from langchain_core.documents import Document
from langchain_core.embeddings import DeterministicFakeEmbedding
from pgvector.psycopg import Vector

from tutor.store import store_chunks


def doc(code: str, text: str, page: int = 10, position: int = 1, kind: str = "THEORY") -> Document:
    return Document(page_content=text, metadata={
        "code": code, "kind": kind, "book_page": page, "source_name": "book.pdf", "position": position})


EMBEDDINGS = DeterministicFakeEmbedding(size=1536)


def rows(conn):
    return conn.execute(
        "SELECT c.code, t.kind, t.book_page, t.source, t.source_name, t.embedded_with "
        "FROM tutor_chunks t JOIN en_chapters c ON c.id = t.chapter_id ORDER BY t.book_page, t.position").fetchall()


def test_chunks_are_stored_on_the_most_specific_existing_page(conn):
    documents = [
        doc("AIS2P2", "Scăderea: $a-b=d$.", position=1),
        doc("AIS2P1", "Adunarea: $a+b=s$.", position=2),  # no AIS2P1 page in the LMS: falls back to AIS2
        doc("AIS9", "Fără pagină proprie, ajunge pe capitol.", position=3),  # falls back to AI
    ]

    report = store_chunks(conn, documents, EMBEDDINGS, "text-embedding-3-small", "book.pdf")

    assert report.inserted == 3
    assert report.unresolved == []
    assert rows(conn) == [
        ("AIS2P2", "THEORY", 10, "PDF", "book.pdf", "text-embedding-3-small"),
        ("AIS2", "THEORY", 10, "PDF", "book.pdf", "text-embedding-3-small"),
        ("AI", "THEORY", 10, "PDF", "book.pdf", "text-embedding-3-small"),
    ]


def test_a_code_with_no_page_at_all_is_reported_not_stored(conn):
    report = store_chunks(conn, [doc("GIII", "Triunghi.")], EMBEDDINGS, "m", "book.pdf")

    assert report.inserted == 0
    assert report.unresolved == ["GIII"]
    assert rows(conn) == []


def test_running_again_changes_nothing_and_embeds_nothing_new(conn):
    documents = [doc("AIS2P2", "Scăderea: $a-b=d$.")]
    store_chunks(conn, documents, EMBEDDINGS, "m", "book.pdf")

    class Boom(DeterministicFakeEmbedding):
        def embed_documents(self, texts):
            raise AssertionError("nothing should be embedded again")

    report = store_chunks(conn, documents, Boom(size=1536), "m", "book.pdf")

    assert (report.inserted, report.unchanged, report.removed) == (0, 1, 0)
    assert len(rows(conn)) == 1


def test_a_changed_page_replaces_only_its_own_old_chunks(conn):
    store_chunks(conn, [doc("AIS2P2", "versiunea veche", page=10), doc("AI", "pagina 9", page=9)],
                 EMBEDDINGS, "m", "book.pdf")

    report = store_chunks(conn, [doc("AIS2P2", "versiunea nouă", page=10)], EMBEDDINGS, "m", "book.pdf")

    assert (report.inserted, report.removed) == (1, 1)
    remaining = conn.execute("SELECT content, book_page FROM tutor_chunks ORDER BY book_page").fetchall()
    assert remaining == [("pagina 9", 9), ("versiunea nouă", 10)]  # page 9 was not part of this run: untouched


def test_the_embedding_is_the_breadcrumb_plus_text_and_has_the_column_size(conn):
    store_chunks(conn, [doc("AIS2P2", "scăderea")], EMBEDDINGS, "m", "book.pdf")

    breadcrumb = "Mulțimea numerelor naturale > Adunarea și scăderea > Adunarea și scăderea: Scăderea"
    query = Vector(EMBEDDINGS.embed_query(f"{breadcrumb}\n\nscăderea"))
    distance, dims = conn.execute(
        "SELECT embedding <=> %s, vector_dims(embedding) FROM tutor_chunks", (query,)).fetchone()

    assert dims == 1536
    assert distance == pytest.approx(0.0, abs=1e-6)  # the stored vector was made from the breadcrumb + the text


def test_chunks_from_photos_point_at_their_photo_and_say_so(conn):
    chapter_id = conn.execute("SELECT id FROM en_chapters WHERE code = 'AI'").fetchone()[0]
    photo_id = conn.execute(
        "INSERT INTO en_chapter_photos (chapter_id, position, image_key) VALUES (%s, 1, 'a.jpg') RETURNING id",
        (chapter_id,)).fetchone()[0]

    store_chunks(conn, [doc("AIS2P2", "din poză", page=10)], EMBEDDINGS, "m", "AI (poze LMS)",
                 source="PHOTO", photo_by_page={10: photo_id})

    assert conn.execute("SELECT source, photo_id FROM tutor_chunks").fetchall() == [("PHOTO", photo_id)]
    # deleting the photo takes its chunks with it
    conn.execute("DELETE FROM en_chapter_photos WHERE id = %s", (photo_id,))
    assert conn.execute("SELECT count(*) FROM tutor_chunks").fetchone()[0] == 0


def test_chunks_embedded_by_another_model_are_embedded_again(conn):
    documents = [doc("AIS2P2", "Scăderea: $a-b=d$.")]
    store_chunks(conn, documents, EMBEDDINGS, "model-vechi", "book.pdf")

    report = store_chunks(conn, documents, EMBEDDINGS, "model-nou", "book.pdf")

    assert (report.inserted, report.removed, report.unchanged) == (1, 1, 0)
    assert [row[5] for row in rows(conn)] == ["model-nou"]


# --- whole_source: the run holds everything the source has (all the photos of a chapter page) ---


def _page_with_photos(conn, code: str, count: int) -> list[int]:
    chapter_id = conn.execute(
        "INSERT INTO en_chapters (code, title, area, position) VALUES (%s, 'Ordinea operațiilor', 'ALGEBRA', 9) "
        "RETURNING id", (code,)).fetchone()[0]
    return [conn.execute(
        "INSERT INTO en_chapter_photos (chapter_id, position, image_key) VALUES (%s, %s, %s) RETURNING id",
        (chapter_id, n, f"{n}.jpg")).fetchone()[0] for n in range(1, count + 1)]


def _photo_rows(conn):
    return conn.execute("SELECT content, book_page, photo_id, position FROM tutor_chunks ORDER BY book_page").fetchall()


def test_a_whole_source_run_gives_kept_chunks_their_new_page_and_photo(conn):
    first, second, third = _page_with_photos(conn, "A1", 3)
    store_chunks(conn, [doc("A1", "prima", page=1), doc("A1", "a doua", page=2), doc("A1", "a treia", page=3)],
                 EMBEDDINGS, "m", "A1 (poze LMS)", source="PHOTO", photo_by_page={1: first, 2: second, 3: third},
                 whole_source=True)
    # The teacher deletes the first photo: its chunk goes with it, and the lesson page now calls the others 1 and 2.
    conn.execute("DELETE FROM en_chapter_photos WHERE id = %s", (first,))

    embedded: list[str] = []

    class Counting(DeterministicFakeEmbedding):
        def embed_documents(self, texts):
            embedded.extend(texts)
            return super().embed_documents(texts)

    report = store_chunks(conn, [doc("A1", "a doua", page=1), doc("A1", "a treia", page=2)],
                          Counting(size=1536), "m", "A1 (poze LMS)", source="PHOTO",
                          photo_by_page={1: second, 2: third}, whole_source=True)

    assert (report.inserted, report.unchanged, report.removed) == (0, 2, 0)
    assert embedded == []  # the same texts: nothing is paid for again
    assert _photo_rows(conn) == [("a doua", 1, second, 1), ("a treia", 2, third, 1)]


def test_a_whole_source_run_drops_what_the_source_no_longer_has_on_any_page(conn):
    first, second = _page_with_photos(conn, "A1", 2)
    photos = {1: first, 2: second}
    store_chunks(conn, [doc("A1", "rămâne", page=1), doc("A1", "dispare", page=2)], EMBEDDINGS, "m", "A1 (poze LMS)",
                 source="PHOTO", photo_by_page=photos, whole_source=True)
    store_chunks(conn, [doc("AI", "din carte", page=9)], EMBEDDINGS, "m", "book.pdf")  # another source: never touched

    # Page 2 gives nothing any more (its photo was transcribed again and came out empty): page 2 is not in the run.
    report = store_chunks(conn, [doc("A1", "rămâne", page=1)], EMBEDDINGS, "m", "A1 (poze LMS)", source="PHOTO",
                          photo_by_page=photos, whole_source=True)

    assert (report.inserted, report.unchanged, report.removed) == (0, 1, 1)
    assert [row[0] for row in conn.execute("SELECT content FROM tutor_chunks ORDER BY book_page")] == ["rămâne", "din carte"]


def test_an_empty_whole_source_run_removes_the_chunks_the_source_had(conn):
    (photo,) = _page_with_photos(conn, "A1", 1)
    store_chunks(conn, [doc("A1", "text", page=1)], EMBEDDINGS, "m", "A1 (poze LMS)", source="PHOTO",
                 photo_by_page={1: photo}, whole_source=True)

    report = store_chunks(conn, [], EMBEDDINGS, "m", "A1 (poze LMS)", source="PHOTO", whole_source=True)

    assert report.removed == 1
    assert conn.execute("SELECT count(*) FROM tutor_chunks").fetchone()[0] == 0
