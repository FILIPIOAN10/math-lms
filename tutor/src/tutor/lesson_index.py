"""Plan step 6: the tutor answers from the lessons the teacher approved. Every chapter lesson that is approved AND
shown to students (en_chapter_lessons, V78; en_chapters.lesson_published) is cut into passages by its sections and
embedded as LESSON chunks (V79); the search prefers them to the raw book pages (retrieval.prefer_lessons). A lesson
taken back (edited, regenerated, hidden) leaves the index at the next run.

A run embeds only what is new or changed (tutor.store.store_chunks keeps the rest). The backend runs it after every
approval and every show / hide through POST /lesson-index; by hand:
    uv run python -X utf8 -m tutor.lesson_index
"""

import re
import sys

import psycopg
from langchain_core.documents import Document
from langchain_core.embeddings import Embeddings

from tutor.chunking import MAX_CHARS, MIN_CHARS, _split
from tutor.config import get_settings
from tutor.llm import embeddings as make_embeddings
from tutor.similar_index import IndexReport
from tutor.store import store_chunks

SOURCE = "LESSON"
_SKIPPED = ("Ce trebuie să știi înainte",)  # pointers to other chapters: their own lessons explain them


def lesson_chunks(markdown: str) -> list[tuple[str, str]]:
    """(section title, passage) in reading order: one section (or problem type) at a time, cut further only when too
    long. Each passage starts with its title, so a passage read alone still says what it is about."""
    out: list[tuple[str, str]] = []
    section, subsection, lines = "", "", []

    def flush() -> None:
        text = "\n".join(lines).strip()
        if text and section and section not in _SKIPPED:
            title = f"{section} · {subsection}" if subsection else section
            out.extend((title, f"{title}\n\n{piece}") for piece in _split(text, MAX_CHARS, MIN_CHARS))
        lines.clear()

    for line in markdown.splitlines():
        heading = re.match(r"^(#{2,3})\s+(.*)$", line)
        if heading:
            flush()
            if heading.group(1) == "##":
                section, subsection = heading.group(2).strip(), ""
            else:
                subsection = heading.group(2).strip()
        else:
            lines.append(line)
    flush()
    return out


def index_lessons(conn: psycopg.Connection, embeddings: Embeddings, embedding_model: str) -> IndexReport:
    """Does not commit: the caller decides."""
    rows = conn.execute(
        "SELECT c.code, l.markdown FROM en_chapter_lessons l JOIN en_chapters c ON c.id = l.chapter_id "
        "WHERE l.status = 'approved' AND c.lesson_published ORDER BY c.position, c.id").fetchall()
    inserted = unchanged = removed = 0
    kept_names = []
    for code, markdown in rows:
        name = f"{code} (lecție aprobată)"
        kept_names.append(name)
        documents = [Document(page_content=text, metadata={"code": code, "kind": "THEORY", "book_page": 0,
                                                            "position": n, "title": title})
                     for n, (title, text) in enumerate(lesson_chunks(markdown), start=1)]
        report = store_chunks(conn, documents, embeddings, embedding_model, name, source=SOURCE, whole_source=True)
        inserted, unchanged, removed = inserted + report.inserted, unchanged + report.unchanged, removed + report.removed
    gone = conn.execute("DELETE FROM tutor_chunks WHERE source = %s AND NOT (source_name = ANY(%s))",
                        (SOURCE, kept_names)).rowcount
    return IndexReport(inserted=inserted, unchanged=unchanged, removed=removed + gone)


def run_lesson_index() -> IndexReport:
    """With the settings: the real embedding model and the LMS database, committed at the end."""
    settings = get_settings()
    with psycopg.connect(settings.psycopg_dsn) as conn:
        report = index_lessons(conn, make_embeddings(settings), settings.embedding_model)
        conn.commit()
    return report


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    report = run_lesson_index()
    print(f"lecții aprobate: {report.inserted} pasaje noi, {report.unchanged} neschimbate, {report.removed} scoase")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
