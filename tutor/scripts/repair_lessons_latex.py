"""Puts back the LaTeX that JSON escapes ate in the stored lessons ("\neq" read as a new line and "eq", "\text" as a tab
and "ext"; see answer_check.repair_page_latex): the page transcripts, and the tutor's chunks with a fresh embedding for
each changed one (a few embedding calls, no chat model). Run lessons-backup.ps1 first.

    uv run python -X utf8 scripts/repair_lessons_latex.py            (shows what would change)
    uv run python -X utf8 scripts/repair_lessons_latex.py --apply
"""

import argparse

import psycopg
from pgvector.psycopg import Vector, register_vector

from tutor.answer_check import repair_page_latex
from tutor.chunking import content_hash
from tutor.config import get_settings
from tutor.llm import embeddings as make_embeddings
from tutor.store import _breadcrumb, _without_figure_links, pages_by_code


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    settings = get_settings()
    with psycopg.connect(settings.psycopg_dsn) as conn:
        register_vector(conn)
        transcripts = [(photo, text, repair_page_latex(text)) for photo, text in
                       conn.execute("SELECT photo_id, content FROM tutor_photo_transcripts").fetchall()]
        transcripts = [t for t in transcripts if t[1] != t[2]]
        chunks = [(cid, chapter, code, text, repair_page_latex(text)) for cid, chapter, code, text in conn.execute(
            "SELECT c.id, c.chapter_id, ch.code, c.content FROM tutor_chunks c JOIN en_chapters ch ON ch.id = c.chapter_id"
        ).fetchall()]
        chunks = [c for c in chunks if c[3] != c[4]]
        print(f"transcrieri de reparat: {len(transcripts)} | fragmente de reparat: {len(chunks)}")
        for _, old, new in transcripts[:3]:
            at = next(i for i, (a, b) in enumerate(zip(old, new)) if a != b)
            print("  ", repr(old[max(0, at - 30):at + 30]), "->", repr(new[max(0, at - 30):at + 30]))
        if not args.apply:
            print("(doar arătat; cu --apply se scrie)")
            return
        for photo, _, new in transcripts:
            conn.execute("UPDATE tutor_photo_transcripts SET content = %s WHERE photo_id = %s", (new, photo))
        embedder = make_embeddings(settings)
        pages = pages_by_code(conn, {code for _, _, code, _, _ in chunks})
        texts = [f"{_breadcrumb(code, pages)}\n\n{_without_figure_links(new)}" for _, _, code, _, new in chunks]
        vectors = embedder.embed_documents(texts) if texts else []
        replaced = removed = 0
        for (cid, chapter, _, _, new), vector in zip(chunks, vectors, strict=True):
            digest = content_hash(new)
            if conn.execute("SELECT 1 FROM tutor_chunks WHERE chapter_id = %s AND content_hash = %s AND id <> %s",
                            (chapter, digest, cid)).fetchone():
                conn.execute("DELETE FROM tutor_chunks WHERE id = %s", (cid,))  # the repaired text is already there
                removed += 1
                continue
            conn.execute("UPDATE tutor_chunks SET content = %s, content_hash = %s, embedding = %s, embedded_with = %s "
                         "WHERE id = %s", (new, digest, Vector(vector), settings.embedding_model, cid))
            replaced += 1
        conn.commit()
        print(f"scris: {len(transcripts)} transcrieri, {replaced} fragmente cu embedding nou, {removed} dubluri șterse")


if __name__ == "__main__":
    main()
