"""„Dă-mi una asemănătoare": one embedding per question of the bank and per worked example of the EN chapter pages, in
similar_index (V71), so the backend can hand a student the nearest ones by meaning after a mistake - plain search with
pgvector, nothing generated. Only the statements are embedded (never a solution, never anything a student wrote).

A run embeds only what is new, changed (content_hash) or embedded by another model; rows of deleted questions go with
them (ON DELETE CASCADE). The backend runs it every night through POST /similar-index; by hand:

    uv run python -X utf8 -m tutor.similar_index [--dry-run]
"""

import argparse
import hashlib
import sys
from dataclasses import dataclass

import psycopg
from langchain_core.embeddings import Embeddings
from pgvector.psycopg import Vector, register_vector

from tutor.config import get_settings
from tutor.llm import embeddings as make_embeddings
from tutor.store import _without_figure_links

BATCH = 100  # texts per embedding request


@dataclass(frozen=True)
class IndexReport:
    inserted: int = 0
    unchanged: int = 0
    removed: int = 0


def _digest(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def _sources(conn: psycopg.Connection) -> dict[tuple[str, int], str]:
    """(kind, id) -> the text to embed: a question's or an example's statement, without blank ones."""
    wanted: dict[tuple[str, int], str] = {}
    for kind, table in (("item", "quiz_items"), ("example", "en_chapter_examples")):
        for source_id, statement in conn.execute(f"SELECT id, statement FROM {table} ORDER BY id").fetchall():
            text = _without_figure_links(statement or "").strip()
            if text:
                wanted[(kind, source_id)] = text
    return wanted


def index_similar(conn: psycopg.Connection, embeddings: Embeddings, embedding_model: str, *,
                  dry_run: bool = False) -> IndexReport:
    """Brings similar_index up to date with the statements. Does not commit: the caller decides."""
    register_vector(conn)
    wanted = _sources(conn)
    existing = {}
    stale: list[int] = []
    for row_id, item_id, example_id, digest, model in conn.execute(
            "SELECT id, item_id, example_id, content_hash, embedded_with FROM similar_index").fetchall():
        key = ("item", item_id) if item_id is not None else ("example", example_id)
        text = wanted.get(key)
        if text is None or model != embedding_model or digest != _digest(text):
            stale.append(row_id)  # gone blank, changed, or not comparable with the rest of the index
        else:
            existing[key] = row_id
    fresh = [key for key in wanted if key not in existing]
    report = IndexReport(inserted=len(fresh), unchanged=len(existing), removed=len(stale))
    if dry_run:
        return report
    if stale:
        conn.execute("DELETE FROM similar_index WHERE id = ANY(%s)", (stale,))
    for start in range(0, len(fresh), BATCH):
        batch = fresh[start:start + BATCH]
        vectors = embeddings.embed_documents([wanted[key] for key in batch])
        for (kind, source_id), vector in zip(batch, vectors, strict=True):
            conn.execute(
                "INSERT INTO similar_index (item_id, example_id, content_hash, embedded_with, embedding) "
                "VALUES (%s, %s, %s, %s, %s)",
                (source_id if kind == "item" else None, source_id if kind == "example" else None,
                 _digest(wanted[(kind, source_id)]), embedding_model, Vector(vector)))
    return report


def run_index() -> IndexReport:
    """With the settings: the real embedding model and the LMS database, committed at the end."""
    settings = get_settings()
    with psycopg.connect(settings.psycopg_dsn) as conn:
        report = index_similar(conn, make_embeddings(settings), settings.embedding_model)
        conn.commit()
    return report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Embedding-uri pentru „Dă-mi una asemănătoare”.")
    parser.add_argument("--dry-run", action="store_true", help="arată ce s-ar face, fără apeluri plătite")
    args = parser.parse_args(argv)
    sys.stdout.reconfigure(encoding="utf-8")
    if args.dry_run:
        settings = get_settings()
        with psycopg.connect(settings.psycopg_dsn) as conn:
            report = index_similar(conn, make_embeddings(settings), settings.embedding_model, dry_run=True)
    else:
        report = run_index()
    print(f"Noi: {report.inserted}, neschimbate: {report.unchanged}, scoase: {report.removed}"
          + (" (simulare)" if args.dry_run else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
