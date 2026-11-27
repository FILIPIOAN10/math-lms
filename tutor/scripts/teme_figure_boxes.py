"""„Teme recapitulative": finds where each exercise's drawing sits on its scanned book page, for the crop under its statement.

The transcription boxed only some figures, so every book page holding an exercise with a figure is shown again to Gemini
with the numbers of those exercises, and it answers one box per drawing. Answers are cached in
tutor/data/teme/figure_boxes.json (git-ignored, like the pages): a page already there is skipped, so a run can be repeated.
The crops themselves are made by scripts/teme_bundle.py, which needs no model.

    uv run --with pymupdf python -X utf8 scripts/teme_figure_boxes.py [--model gemini-3.8-flash] [--pages 37 38]
"""

import argparse
import concurrent.futures
import glob
import json
import pathlib

import fitz
from google.genai import types

from tutor.config import Settings
from tutor.transcribe import gemini_client

SOURCES = r"C:\Users\Filip\Desktop\Teste\Invata_cu_succes\TEME RECAPITULATIVE_*.pdf"
DATA = pathlib.Path(__file__).resolve().parent.parent / "data" / "teme"
BOXES = DATA / "figure_boxes.json"
MAX_SIDE = 2400
# The scans start at book page 20: book page N is the (N - 19)th page of the two PDFs read one after the other.
FIRST_BOOK_PAGE = 20

INSTRUCTIONS = """You see one scanned page of a Romanian maths exercise book. Some exercises on it come with a printed
drawing (a geometric figure, a graph in axes, a solid, a number line, a table drawn as a picture). You are told the
numbers of the exercises whose drawing must be found. For each of them, return the box of EVERY drawing that belongs to
it - usually one, sometimes several - as box_2d = [ymin, xmin, ymax, xmax] on a 0..1000 scale of the page. Drawings are
often labelled "Figura N" with N the exercise number, or sit right next to the exercise's text. A box holds the whole
drawing with its letters and its "Figura N" caption, and no line of exercise text. If an exercise's drawing is not on
this page, return it with no boxes. Never box a drawing for an exercise you were not asked about."""

SCHEMA = {
    "type": "object",
    "properties": {
        "exercises": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "number": {"type": "integer"},
                    "boxes": {"type": "array", "items": {"type": "array", "items": {"type": "integer"}}},
                },
                "required": ["number", "boxes"],
            },
        },
    },
    "required": ["exercises"],
}


def pdf_pages() -> list[tuple[str, int]]:
    out = []
    for path in sorted(glob.glob(SOURCES)):
        for index in range(len(fitz.open(path))):
            out.append((path, index))
    return out


def render(book_page: int, pages: list[tuple[str, int]]) -> bytes:
    """The page as a JPEG, long side MAX_SIDE (the same picture teme_bundle.py crops from)."""
    path, index = pages[book_page - FIRST_BOOK_PAGE]
    page = fitz.open(path)[index]
    zoom = MAX_SIDE / max(page.rect.width, page.rect.height)
    return page.get_pixmap(matrix=fitz.Matrix(zoom, zoom)).tobytes("jpeg")


def wanted() -> dict[int, list[int]]:
    """Book page -> the numbers of the exercises on it that have a figure (an exercise on two pages counts on both)."""
    exercises = json.loads((DATA / "exercises.json").read_text(encoding="utf-8"))
    out: dict[int, list[int]] = {}
    for ex in exercises:
        if ex["has_figure"]:
            for page in ex["pages"]:
                out.setdefault(page, []).append(ex["number"])
    return out


def on_next_page(cache: dict) -> dict[int, list[int]]:
    """Exercises with a figure found on none of their pages: their drawing is often printed at the top of the next one
    (T9-5..8). Next page -> numbers, leaving out a number that an exercise of that page uses itself (no mix-up)."""
    exercises = json.loads((DATA / "exercises.json").read_text(encoding="utf-8"))
    own = wanted()
    out: dict[int, list[int]] = {}
    for ex in exercises:
        if not ex["has_figure"]:
            continue
        if any(cache.get(str(p), {}).get(str(ex["number"])) for p in ex["pages"]):
            continue
        after = max(ex["pages"]) + 1
        if ex["number"] not in own.get(after, []):
            out.setdefault(after, []).append(ex["number"])
    return out


def locate(book_page: int, numbers: list[int], jpeg: bytes, model: str) -> dict:
    client = gemini_client(Settings())
    config = types.GenerateContentConfig(
        system_instruction=INSTRUCTIONS,
        response_mime_type="application/json",
        response_json_schema=SCHEMA,
        max_output_tokens=4000,
        thinking_config=types.ThinkingConfig(thinking_level=types.ThinkingLevel.LOW),
    )
    ask = "Find the drawings of exercises: " + ", ".join(str(n) for n in numbers) + "."
    for _ in range(3):  # a preview model sometimes answers empty under load
        response = client.models.generate_content(
            model=model, contents=[types.Part.from_bytes(data=jpeg, mime_type="image/jpeg"), ask], config=config)
        if response.text:
            break
    else:
        return {"page": book_page, "error": "empty"}
    data = json.loads(response.text)
    boxes = {}
    for item in data.get("exercises", []):
        good = [b for b in item.get("boxes", []) if len(b) == 4 and b[0] < b[2] and b[1] < b[3]]
        if item.get("number") in numbers:
            boxes[str(item["number"])] = [[max(0, min(1000, v)) for v in b] for b in good]
    usage = response.usage_metadata
    return {"page": book_page, "boxes": boxes,
            "in": usage.prompt_token_count or 0 if usage else 0, "out": usage.candidates_token_count or 0 if usage else 0}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="gemini-3.8-flash")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--pages", type=int, nargs="*", help="only these book pages (done again even if cached)")
    parser.add_argument("--next", action="store_true",
                        help="look on the next page for the drawings found nowhere (kept apart, under \"next\")")
    args = parser.parse_args()
    cache = json.loads(BOXES.read_text(encoding="utf-8")) if BOXES.exists() else {}
    target = cache.setdefault("next", {}) if args.next else cache
    todo = on_next_page(cache) if args.next else wanted()
    if args.next:
        todo = {p: n for p, n in todo.items() if str(p) not in target}
    elif args.pages:
        todo = {p: n for p, n in todo.items() if p in args.pages}
    else:
        todo = {p: n for p, n in todo.items() if str(p) not in cache}
    pages = pdf_pages()
    print(f"{len(todo)} pagini de citit, model {args.model}")

    def one(item):
        page, numbers = item
        return locate(page, numbers, render(page, pages), args.model)

    with concurrent.futures.ThreadPoolExecutor(args.workers) as pool:
        for result in pool.map(one, sorted(todo.items())):
            if "error" in result:
                print("pagina", result["page"], "fără răspuns")
                continue
            target[str(result["page"])] = result["boxes"]
            empty = [n for n in todo[result["page"]] if not result["boxes"].get(str(n))]
            print(f"pagina {result['page']}: {len(result['boxes'])} exerciții, fără desen: {empty or '-'}"
                  f" ({result['in']}/{result['out']} tokeni)")
            BOXES.write_text(json.dumps(cache, indent=1, sort_keys=True), encoding="utf-8")


if __name__ == "__main__":
    main()
