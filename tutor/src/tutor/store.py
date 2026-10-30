"""Writes chunks to tutor_chunks (pgvector), replacing exactly what the same pages produced before."""

import re
from dataclasses import dataclass, field

import psycopg
from langchain_core.documents import Document
from langchain_core.embeddings import Embeddings
from pgvector.psycopg import Vector, register_vector

from tutor import page_map
from tutor.chunking import content_hash

@dataclass
class StoreReport:
    inserted: int = 0
    unchanged: int = 0
    removed: int = 0
    unresolved: list[str] = field(default_factory=list)  # chunk codes with no chapter page in the LMS
    by_code: dict[str, int] = field(default_factory=dict)


def pages_by_code(conn: psycopg.Connection, codes: set[str]) -> dict[str, tuple[int, str]]:
    wanted = {parent for code in codes for parent in page_map.parents(code)}
    rows = conn.execute("SELECT code, id, title FROM en_chapters WHERE code = ANY(%s)", (sorted(wanted),)).fetchall()
    return {code: (chapter_id, title) for code, chapter_id, title in rows}


def resolve(code: str, pages: dict[str, tuple[int, str]]) -> str | None:
    """The most specific LMS page that exists: AIS5P5, else AIS5, else AI."""
    return next((c for c in page_map.parents(code) if c in pages), None)


def _without_figure_links(text: str) -> str:
    """A figure's address means nothing to an embedding; its description does."""
    return re.sub(r"!\[([^\]]*)\]\(poza:[^)]*\)", r"[figură: \1]", text)


def _breadcrumb(code: str, pages: dict[str, tuple[int, str]]) -> str:
    titles = [pages[c][1] for c in reversed(page_map.parents(code)) if c in pages]
    return " > ".join(titles)


def store_chunks(conn: psycopg.Connection, documents: list[Document], embeddings: Embeddings,
                 embedding_model: str, source_name: str, *, source: str = "PDF",
                 photo_by_page: dict[int, int] | None = None) -> StoreReport:
    """
    Does not commit: the caller decides (a dry run or a test rolls back). `source` is where the text came from
    (PDF file, or PHOTO uploaded on a chapter page, in which case `photo_by_page` links each chunk to its photo).
    """
    register_vector(conn)
    report = StoreReport()
    pages = pages_by_code(conn, {d.metadata["code"] for d in documents})

    wanted: dict[tuple[int, str], tuple[Document, str]] = {}  # (page id, hash) -> (document, text to embed)
    for document in documents:
        code = resolve(document.metadata["code"], pages)
        if code is None:
            report.unresolved.append(document.metadata["code"])
            continue
        chapter_id = pages[code][0]
        key = (chapter_id, content_hash(document.page_content))
        embed_text = f"{_breadcrumb(code, pages)}\n\n{_without_figure_links(document.page_content)}"
        wanted[key] = (document.model_copy(update={"metadata": {**document.metadata, "chapter_id": chapter_id}}), embed_text)
        report.by_code[code] = report.by_code.get(code, 0) + 1

    book_pages = sorted({d.metadata["book_page"] for d in documents})
    existing = conn.execute(
        "SELECT id, chapter_id, content_hash, embedded_with FROM tutor_chunks "
        "WHERE source = %s AND source_name = %s AND book_page = ANY(%s)",
        (source, source_name, book_pages),
    ).fetchall()
    # A chunk embedded by another model is not comparable with the rest of the index: it counts as gone, and comes back.
    existing_keys = {(chapter_id, digest): row_id for row_id, chapter_id, digest, model in existing
                     if model == embedding_model}
    outdated = [row[0] for row in existing if row[3] != embedding_model]

    stale = outdated + [row_id for key, row_id in existing_keys.items() if key not in wanted]
    if stale:
        conn.execute("DELETE FROM tutor_chunks WHERE id = ANY(%s)", (stale,))
        report.removed = len(stale)

    fresh = [key for key in wanted if key not in existing_keys]
    report.unchanged = len(wanted) - len(fresh)
    if fresh:
        vectors = embeddings.embed_documents([wanted[key][1] for key in fresh])
        for key, vector in zip(fresh, vectors, strict=True):
            document = wanted[key][0]
            photo_id = (photo_by_page or {}).get(document.metadata["book_page"])
            conn.execute(
                "INSERT INTO tutor_chunks (chapter_id, photo_id, kind, position, content, content_hash, embedded_with, "
                "embedding, source, source_name, book_page) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s) "
                "ON CONFLICT (chapter_id, content_hash) DO NOTHING",
                (key[0], photo_id, document.metadata["kind"], document.metadata["position"], document.page_content,
                 key[1], embedding_model, Vector(vector), source, source_name, document.metadata["book_page"]),
            )
        report.inserted = len(fresh)
    return report
