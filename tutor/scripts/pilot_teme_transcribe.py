"""Pilot, step 1: transcribes the scanned "Teme recapitulative" pages (two PDFs) with the strong model.

Nothing here touches the app or its database: the pages are rendered from the PDFs, read by Gemini and written to
tutor/data/teme/<n>.md (git-ignored; the pages are from a commercial book). A page already written is skipped, so a run
can be repeated after a failure.

    uv run --with pymupdf python -X utf8 scripts/pilot_teme_transcribe.py [--model gemini-3.1-pro-preview]
"""

import argparse
import concurrent.futures
import glob
import json
import pathlib
import time

import fitz

from tutor.config import Settings
from tutor.transcribe import PageImage, TranscriptionError, prepare, render_markdown, transcribe_with_gemini

SOURCES = r"C:\Users\Filip\Desktop\Teste\Invata_cu_succes\TEME RECAPITULATIVE_*.pdf"
OUT = pathlib.Path(__file__).resolve().parent.parent / "data" / "teme"
MAX_SIDE = 2400


def pages() -> list[tuple[str, int]]:
    out = []
    for path in sorted(glob.glob(SOURCES)):
        for index in range(len(fitz.open(path))):
            out.append((path, index))
    return out


def transcribe_one(item: tuple[str, int, int], model: str) -> dict:
    path, index, order = item
    target = OUT / f"page-{order:02d}.md"
    if target.exists():
        return {"order": order, "skipped": True}
    page = fitz.open(path)[index]
    zoom = MAX_SIDE / max(page.rect.width, page.rect.height)
    raw = page.get_pixmap(matrix=fitz.Matrix(zoom, zoom)).tobytes("jpeg")
    jpeg, width, height = prepare(raw, max_side=MAX_SIDE)
    settings = Settings()
    settings.transcribe_model = model
    started = time.time()
    try:
        result = transcribe_with_gemini(jpeg, settings)
    except TranscriptionError as error:
        return {"order": order, "error": str(error)}
    text = render_markdown("TEME", PageImage(order, jpeg, width, height), result, 0.0)
    target.write_text(text, encoding="utf-8")
    return {"order": order, "book_page": result.page_number, "seconds": round(time.time() - started),
            "in": result.input_tokens, "out": result.output_tokens}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="gemini-3.1-pro-preview")
    parser.add_argument("--workers", type=int, default=3)
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    items = [(path, index, order) for order, (path, index) in enumerate(pages(), start=1)]
    print(f"{len(items)} pagini, model {args.model}")
    totals = {"in": 0, "out": 0}
    with concurrent.futures.ThreadPoolExecutor(args.workers) as pool:
        for result in pool.map(lambda item: transcribe_one(item, args.model), items):
            print(json.dumps(result, ensure_ascii=False))
            totals["in"] += result.get("in", 0)
            totals["out"] += result.get("out", 0)
    print("tokeni:", totals)


if __name__ == "__main__":
    main()
