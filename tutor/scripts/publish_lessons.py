"""Writes the book pages written out into the app (V73): for every lesson photo, its text (tutor.lesson_text) and the PNG
of every figure the app drew (pilot_figures_redraw.py: data/theory/redraw-all, with the comparison with the book and
the fill pass). No model is called. Students see a chapter's pages only after the teacher shows them on the chapter
page ("Arată-o elevilor"); running this again replaces the text and the drawings, and keeps that choice.

    uv run python -X utf8 scripts/publish_lessons.py --dry-run
    uv run python -X utf8 scripts/publish_lessons.py [--chapters G5,G6]
"""

import argparse
import collections
import pathlib
import re
import sys

import psycopg

from tutor import figures
from tutor.config import get_settings
from tutor.lesson_text import MARKER, Placed, app_markdown

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from clean_lessons import THEORY, drawings  # noqa: E402 - the same merged drawings the clean pages show


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--redraw", default="redraw-all", help="the folder under data/theory with the drawings")
    parser.add_argument("--chapters", default="", help="only these chapter codes, comma separated")
    parser.add_argument("--dry-run", action="store_true", help="draw and count everything, write nothing")
    args = parser.parse_args()
    drawn = drawings(THEORY / args.redraw)
    wanted = {c.strip() for c in args.chapters.split(",") if c.strip()}
    settings = get_settings()
    counts = collections.Counter()
    with psycopg.connect(settings.psycopg_dsn) as conn:
        pages = conn.execute(
            """SELECT ch.code, p.id, t.content FROM en_chapter_photos p JOIN en_chapters ch ON ch.id = p.chapter_id
               JOIN tutor_photo_transcripts t ON t.photo_id = p.id ORDER BY ch.position, p.position""").fetchall()
        for code, photo, transcript in pages:
            if wanted and code not in wanted:
                continue
            body = re.sub(r"^---\n.*?\n---\n", "", transcript, flags=re.S)
            placed: dict[int, Placed] = {}
            if not args.dry_run:
                conn.execute("DELETE FROM en_photo_drawings WHERE photo_id = %s", (photo,))
            for number in dict.fromkeys(int(n) for n in MARKER.findall(body)):
                entry = drawn.get((photo, number)) or {}
                description = str(entry.get("description") or "")
                if entry.get("as_text"):
                    placed[number] = Placed(description, text=entry["as_text"])
                    counts["ca text"] += 1
                    continue
                if entry.get("drawable") and entry.get("figure") and (entry.get("check") or {}).get("ok", True):
                    try:
                        png = figures.render(entry["figure"])[0]
                    except Exception:  # noqa: BLE001 - left to the book page
                        png = None
                    if png is not None:
                        drawing_id = 0 if args.dry_run else conn.execute(
                            "INSERT INTO en_photo_drawings (photo_id, number, description, png) VALUES (%s, %s, %s, %s) "
                            "RETURNING id", (photo, number, description, png)).fetchone()[0]
                        placed[number] = Placed(description, drawing_id=drawing_id)
                        counts["desene"] += 1
                        continue
                placed[number] = Placed(description)
                counts["spre pagina din carte"] += 1
            markdown = app_markdown(transcript, placed)
            page_number = re.search(r"^book_page:\s*(\d+)", transcript, re.M)  # V74: "pagina N" links find it
            counts["pagini"] += 1
            if not args.dry_run:
                conn.execute(
                    "INSERT INTO en_photo_lessons (photo_id, markdown, book_page) VALUES (%s, %s, %s) "
                    "ON CONFLICT (photo_id) DO UPDATE SET markdown = EXCLUDED.markdown, book_page = EXCLUDED.book_page, "
                    "updated_at = now()", (photo, markdown, int(page_number.group(1)) if page_number else None))
        if args.dry_run:
            conn.rollback()
        else:
            conn.commit()
    print(("(doar numărat) " if args.dry_run else "scris: ") + ", ".join(f"{k} {v}" for k, v in counts.items()))


if __name__ == "__main__":
    main()
