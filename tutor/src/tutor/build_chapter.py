"""Builds the written theory of one chapter from the photos uploaded on its page in the LMS.

Run:  uv run python -m tutor.build_chapter AI --max-usd 0.10

1. each photo of the chapter is read by a vision model once (the text is kept in tutor_photo_transcripts);
2. the text is cut at the numbered headings into chunks, embedded and stored for the bot (tutor_chunks);
3. each section's text is written into that section's page in the app (en_chapters.notes), where students read it.
"""

import argparse
import pathlib
import sys
from dataclasses import dataclass, replace

import httpx
import psycopg

from tutor import chunking
from tutor.chunking import Page, link_figures, parse_transcript
from tutor.config import get_settings
from tutor.llm import embeddings
from tutor.publish import publish_notes
from tutor.store import store_chunks
from tutor.transcribe import PageImage, TranscriptionError, cost_usd, prepare, render_markdown, transcribe_page


@dataclass(frozen=True)
class Photo:
    id: int
    position: int
    image_key: str


def lacks_figures(content: str) -> bool:
    """A page transcribed before figure boxes existed: it mentions a figure but says nowhere where it is."""
    return "[figur" in content and "\nfigures:" not in content


def chapter_row(conn: psycopg.Connection, code: str) -> tuple[int, str, int | None]:
    row = conn.execute("SELECT id, title, book_page FROM en_chapters WHERE code = %s", (code.upper(),)).fetchone()
    if row is None:
        raise SystemExit(f"Nu există capitolul {code} în LMS.")
    return row


def photos_of(conn: psycopg.Connection, chapter_id: int) -> list[Photo]:
    rows = conn.execute(
        "SELECT id, position, image_key FROM en_chapter_photos WHERE chapter_id = %s ORDER BY position, id",
        (chapter_id,)).fetchall()
    return [Photo(*row) for row in rows]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Transformă pozele unui capitol din LMS în text scris, pe subcapitole.")
    parser.add_argument("code", help="codul capitolului, de exemplu AI")
    parser.add_argument("--max-usd", type=float, default=0.25, help="plafon de cost pentru transcriere")
    parser.add_argument("--max-side", type=int, default=1800)
    parser.add_argument("--dry-run", action="store_true", help="arată ce s-ar face, fără apeluri și fără scriere")
    parser.add_argument("--no-publish", action="store_true", help="nu scrie textul în paginile subcapitolelor")
    parser.add_argument("--overwrite", action="store_true", help="înlocuiește și textul scris deja de profesor")
    parser.add_argument("--redo-figures", action="store_true",
                        help="retranscrie paginile cu figuri care n-au poziția figurilor (transcrise înainte să existe)")
    args = parser.parse_args(argv)

    settings = get_settings()
    with psycopg.connect(settings.psycopg_dsn) as conn:
        chapter_id, title, first_page = chapter_row(conn, args.code)
        photos = photos_of(conn, chapter_id)
        if not photos:
            print(f"{args.code.upper()} ({title}): nicio poză încărcată pe pagina capitolului.")
            return 1
        done = dict(conn.execute(
            "SELECT photo_id, content FROM tutor_photo_transcripts WHERE photo_id = ANY(%s)",
            ([p.id for p in photos],)).fetchall())
        todo = [p for p in photos if p.id not in done or (args.redo_figures and lacks_figures(done[p.id]))]
        print(f"{args.code.upper()} · {title}: {len(photos)} poze, {len(photos) - len(todo)} deja transcrise, "
              f"{len(todo)} de transcris | model {settings.chat_model} | plafon {args.max_usd:.2f} USD")
        if args.dry_run:
            print("Dry-run: nu s-a trimis și nu s-a scris nimic.")
            return 0

        images_dir = pathlib.Path(settings.en_chapter_images_dir)
        spent = 0.0
        with httpx.Client(timeout=180) as client:
            for photo in todo:
                if spent >= args.max_usd:
                    print(f"Plafon atins ({spent:.4f} USD): mă opresc înainte de poza {photo.position}.")
                    break
                path = images_dir / photo.image_key
                if not path.is_file():
                    print(f"  poza {photo.position}: fișierul lipsește ({path.name}), sărită")
                    continue
                jpeg, width, height = prepare(path.read_bytes(), max_side=args.max_side)
                try:
                    result = transcribe_page(jpeg, settings, client)
                except TranscriptionError as error:
                    print(f"  poza {photo.position}: EROARE: {error}")
                    return 1
                cost = cost_usd(result.input_tokens, result.output_tokens, settings)
                spent += cost
                content = render_markdown(f"{args.code.upper()} (LMS)", PageImage(photo.position, jpeg, width, height),
                                          result, cost)
                conn.execute(
                    "INSERT INTO tutor_photo_transcripts (photo_id, content, transcribed_with) VALUES (%s, %s, %s) "
                    "ON CONFLICT (photo_id) DO UPDATE SET content = EXCLUDED.content, "
                    "transcribed_with = EXCLUDED.transcribed_with, created_at = now()",
                    (photo.id, content, result.model))
                conn.commit()  # keep what is done if a later page fails
                done[photo.id] = content
                print(f"  poza {photo.position}: pagina din carte {result.page_number} | "
                      f"{result.input_tokens} in, {result.output_tokens} out | ~{cost:.4f} USD")
        print(f"Transcriere: {spent:.4f} USD")

        pages: list[Page] = []
        photo_by_page: dict[int, int] = {}
        for photo in photos:
            if photo.id not in done:
                continue
            page = parse_transcript(done[photo.id], f"{args.code.upper()} (poze LMS)")
            fallback = first_page + photo.position - 1 if first_page is not None else None
            page = replace(page, book_page=page.book_page if page.book_page is not None else fallback)
            pages.append(link_figures(page, photo.id))  # each [figura N] becomes the part of the photo that shows it
            if page.book_page is not None:
                photo_by_page[page.book_page] = photo.id

        documents, problems = chunking.chunk_pages(pages)
        for problem in problems:
            print("  ATENȚIE:", problem)
        source_name = f"{args.code.upper()} (poze LMS)"
        report = store_chunks(conn, documents, embeddings(settings), settings.embedding_model, source_name,
                              source="PHOTO", photo_by_page=photo_by_page)
        print(f"Chunk-uri pentru bot: {report.inserted} noi, {report.unchanged} neschimbate, {report.removed} înlocuite.")

        if not args.no_publish:
            published = publish_notes(conn, documents, overwrite=args.overwrite)
            print(f"Text în aplicație: {len(published.updated)} subcapitole scrise, {len(published.kept)} păstrate "
                  f"(au text de la profesor), {len(published.empty)} fără text, {len(published.too_long)} prea lungi.")
            for code in published.too_long:
                print("  prea lung pentru o pagină:", code)
        conn.commit()
    return 0


if __name__ == "__main__":
    sys.exit(main())
