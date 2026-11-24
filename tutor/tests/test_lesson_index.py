"""Plan step 6: the tutor answers from the lessons the teacher approved. An approved and shown lesson is cut into
passages by its sections (LESSON chunks); the search puts them first when they are about the exercise."""

from fastapi.testclient import TestClient
from langchain_core.embeddings import DeterministicFakeEmbedding
from pydantic import SecretStr

from tutor.api import create_app, lesson_indexer
from tutor.config import Settings
from tutor.lesson_index import index_lessons, lesson_chunks
from tutor.retrieval import Hit, prefer_lessons
from tutor.similar_index import IndexReport

MARKDOWN = """## Ce trebuie să știi înainte

- **Calcul algebric** (A10): înlocuim $x$.

## Ideea pe scurt

Graficul funcției liniare este o dreaptă.

## Formule și proprietăți

- Pentru $a \\neq 0$, intersecția cu $Ox$ este $\\left(-\\frac{b}{a}, 0\\right)$.

## Tipuri de probleme EN

### Intersecția cu axele

**Metoda:**

1. Punem $f(x) = 0$.
"""


def lesson(conn, code: str, status: str, published: bool) -> int:
    chapter = conn.execute("SELECT id FROM en_chapters WHERE code = %s", (code,)).fetchone()[0]
    conn.execute("UPDATE en_chapters SET lesson_published = %s WHERE id = %s", (published, chapter))
    conn.execute("INSERT INTO en_chapter_lessons (chapter_id, markdown, claims_json, sources_json, status) "
                 "VALUES (%s, %s, '[]', '[]', %s) ON CONFLICT (chapter_id) DO UPDATE SET status = EXCLUDED.status, "
                 "markdown = EXCLUDED.markdown", (chapter, MARKDOWN, status))
    return chapter


def lesson_rows(conn) -> list[tuple]:
    return conn.execute("SELECT c.code, t.kind, t.book_page, t.source_name, t.content FROM tutor_chunks t "
                        "JOIN en_chapters c ON c.id = t.chapter_id WHERE t.source = 'LESSON' ORDER BY t.position"
                        ).fetchall()


def test_a_lesson_is_cut_by_its_sections_and_each_passage_names_its_section():
    chunks = lesson_chunks(MARKDOWN)

    assert [title for title, _ in chunks] == ["Ideea pe scurt", "Formule și proprietăți",
                                              "Tipuri de probleme EN · Intersecția cu axele"]
    assert chunks[2][1].startswith("Tipuri de probleme EN · Intersecția cu axele\n\n**Metoda:**")
    assert all("Ce trebuie să știi" not in text for _, text in chunks)  # pointers to other chapters, not theory


def test_only_approved_and_shown_lessons_are_indexed_and_a_withdrawn_one_leaves(conn):
    model = DeterministicFakeEmbedding(size=1536)
    lesson(conn, "AI", "approved", published=True)
    lesson(conn, "AIS2", "approved", published=False)  # approved, but the chapter is not shown
    lesson(conn, "AIS2P2", "draft", published=True)

    report = index_lessons(conn, model, "fake")

    rows = lesson_rows(conn)
    assert report.inserted == 3 and {r[0] for r in rows} == {"AI"}
    assert {(r[1], r[2], r[3]) for r in rows} == {("THEORY", 0, "AI (lecție aprobată)")}

    assert index_lessons(conn, model, "fake").unchanged == 3  # nothing paid twice
    conn.execute("UPDATE en_chapter_lessons SET status = 'draft'")
    assert index_lessons(conn, model, "fake").removed == 3 and lesson_rows(conn) == []


def hit(i: int, score: float) -> Hit:
    return Hit(i, "AI", 0, f"pasajul {i}", score)


def test_approved_lesson_passages_come_first_when_they_are_about_the_exercise():
    book = [hit(1, 0.20), hit(2, 0.22), hit(3, 0.25), hit(4, 0.30)]

    near = prefer_lessons(book, [hit(10, 0.21), hit(11, 0.23), hit(12, 0.24)], k=4)
    assert [h.chunk_id for h in near] == [10, 11, 1, 2]

    far = prefer_lessons(book, [hit(10, 0.40)], k=4)  # the lesson is about something else
    assert far == book
    assert prefer_lessons(book, [], k=4) == book
    assert [h.chunk_id for h in prefer_lessons([hit(10, 0.21)] + book[:3], [hit(10, 0.21)], k=4)] == [10, 1, 2, 3]


def test_the_service_indexes_the_lessons_for_the_backend():
    settings = Settings(_env_file=None, service_token=SecretStr("tok"))
    app = create_app(settings)
    app.dependency_overrides[lesson_indexer] = lambda: (lambda: IndexReport(inserted=5, unchanged=2, removed=1))
    client = TestClient(app)

    response = client.post("/lesson-index", headers={"X-Tutor-Token": "tok"})

    assert response.status_code == 200 and response.json() == {"inserted": 5, "unchanged": 2, "removed": 1}
    assert client.post("/lesson-index").status_code == 401
