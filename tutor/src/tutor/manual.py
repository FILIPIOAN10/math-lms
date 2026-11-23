"""A whole school textbook (grades 5-8) into the tutor's store, so drafts and hints find rules the class lessons lack.

    manuale/<id>.json (printed pages of each lesson -> EN chapter)
      -> transcribe: each page rendered from the PDF and read by the vision model -> data/manuale/<id>/pNNN.md
      -> ingest: the pages of each lesson cut into passages under its chapter -> tutor_chunks (source PDF)

Run:  uv run python -m tutor.manual transcribe manuale/cls5.json --max-usd 1.5
      uv run python -m tutor.manual ingest manuale/cls5.json [--dry-run]

The map is written from the textbook's Cuprins, lesson by lesson; pages it leaves out (tests, exercise pages without
theory) are never read. The printed page of a transcript comes from the map, never from the model. Transcripts stay in
data/ (ignored by git): the text belongs to the publisher, only the map is in the repository.
"""

import argparse
import io
import json
import pathlib
import re
import sys
from dataclasses import dataclass

import httpx
import psycopg
import pypdfium2 as pdfium
import tiktoken
from langchain_core.documents import Document

from tutor.chunking import Page, chunk_pages, read_transcript
from tutor.config import get_settings
from tutor.llm import embeddings
from tutor.store import store_chunks
from tutor.transcribe import TranscriptionError, cost_usd, prepare, transcribe_page

OUT = pathlib.Path("data/manuale")

# Not the grade-8 book's instructions (its heading depths, its watermark): a textbook page of grades 5-8, rendered from
# the publisher's PDF, where the rules often sit inside drawings ("P = 4·L" written inside a square) - they must reach
# the text, or the tutor never finds them.
MANUAL_INSTRUCTIONS = """\
You transcribe ONE page of a Romanian school mathematics textbook (grades 5 to 8) into Markdown.

Rules:
- Write the text exactly as printed, in Romanian, with diacritics. Do not summarise, translate, correct or add anything.
- The lesson title at the top of the page is '##'. Box labels such as 'Important', 'Observă și descoperă!',
  'Exersează!', 'Amintește-ți!', 'Exemplu', 'Rezolvare' stay as bold labels on their own line. Keep the number of
  each exercise at the start of its line ('3 Transformă în metri:'). Running headers and footers (the subject name,
  the chapter name at the bottom, the page number) are left out.
- Mathematics goes in LaTeX between $...$ (inline) or $$...$$ (display): fractions \\frac{a}{b}, powers a^{n},
  roots \\sqrt{x}, \\cdot for multiplication, \\neq, \\leq, angles \\angle or \\sphericalangle, degrees ^\\circ.
  A printed dot between numbers is multiplication, a dash is subtraction: never swap them. Number sets are
  \\mathbb{N}, \\mathbb{Z}, \\mathbb{Q}, \\mathbb{R}.
- Never put Markdown emphasis (** or _) inside a formula or inside \\text{...}; emphasis goes outside the $...$.
- Tables become Markdown tables.
- A box of formulas or text that only has a frame around it is text: transcribe it.
- Where a drawing is printed (a figure, a diagram, a number line, arrows between units, a picture), write [figura N]
  on its own line (N = 1, 2, ... in reading order) and list it in `figures` with a box (x, y = top-left corner,
  width, height, all fractions 0 to 1 of the image) and a Romanian description of what it shows that COPIES every
  formula, label, measure and result written in or next to it, in LaTeX: "Pătrat cu latura $L$: $P = 4 \\cdot L$";
  "Dreptunghi cu lungimea $L$ și lățimea $l$: $P = 2 \\cdot L + 2 \\cdot l = 2 \\cdot (L + l)$"; "Șirul km, hm,
  dam, m, dm, cm, mm: spre dreapta $\\cdot 10$, spre stânga $: 10$". A picture with nothing mathematical (a child
  writing) is described in three words. Every [figura N] in the text has its entry in `figures`, also inside a
  table cell; an angle drawn next to its name is described by its measure ("unghi ascuțit, sub $90^\\circ$").
- If something is unreadable write [?] instead of guessing.
- page_number is the printed page number, or null if you cannot see one.
"""
EMBEDDING_PRICE_PER_MILLION = 0.02  # text-embedding-3-small, USD
_FIGURE = re.compile(r"\[figura\s+(\d+)\]", re.IGNORECASE)


@dataclass(frozen=True)
class Lesson:
    first: int
    last: int
    code: str
    title: str


@dataclass(frozen=True)
class ManualSpec:
    id: str
    name: str
    pdf: pathlib.Path
    pdf_offset: int  # PDF page = printed page + offset (the cover and the title pages come first)
    lessons: tuple[Lesson, ...]

    def pages(self) -> list[int]:
        return sorted(page for lesson in self.lessons for page in range(lesson.first, lesson.last + 1))

    def lesson_of(self, page: int) -> Lesson | None:
        return next((lesson for lesson in self.lessons if lesson.first <= page <= lesson.last), None)

    def chapter_of(self, page: int) -> str | None:
        lesson = self.lesson_of(page)
        return lesson.code if lesson else None


def load_spec(path: pathlib.Path) -> ManualSpec:
    data = json.loads(path.read_text(encoding="utf-8"))
    lessons = tuple(Lesson(int(first), int(last), str(code), str(title)) for first, last, code, title in data["lessons"])
    seen: dict[int, str] = {}
    for lesson in lessons:
        if lesson.last < lesson.first:
            raise ValueError(f"lecția {lesson.title!r}: pagina {lesson.last} e înainte de {lesson.first}")
        for page in range(lesson.first, lesson.last + 1):
            if page in seen:
                raise ValueError(f"pagina {page} e în două lecții: {seen[page]!r} și {lesson.title!r}")
            seen[page] = lesson.title
    return ManualSpec(path.stem, data["name"], pathlib.Path(data["pdf"]), int(data.get("pdf_offset", 0)), lessons)


def pdf_page_of(manual: ManualSpec, printed: int) -> int:
    return printed + manual.pdf_offset


def markdown_page(name: str, pdf_page: int, book_page: int, markdown: str, model: str, cost: float) -> str:
    """A transcript in the shape tutor.chunking reads: front matter, then the Markdown."""
    header = ["---", f"source_pdf: {name}", f"pdf_page: {pdf_page}", f"book_page: {book_page}", f"model: {model}",
              f"cost_usd: {cost:.5f}", "---", ""]
    return "\n".join(header + [markdown.strip(), ""])


# Boxes of a lesson page that hold exercises, not rules. Searched, they are close in meaning to a test question and push
# the rule out of the few passages the model gets (measured 2026-11-23: 27/36 drafts before the grade-5 book, 21/36
# with all of it): only the rules, the worked problems and the unlabelled text are kept.
_EXERCISE_BOXES = (
    # Intuitext (grade 5)
    "observă și descoperă", "descoperă", "exersează", "amintește-ți", "portofoliu", "joc", "autoevaluare",
    "știați că", "proiect", "calculează rapid", "lucrați", "exersezi și progresezi",
    # Litera, Art Klett, Paralela 45 (grades 6-8); "Aplicații ale ..." is a lesson title, so not "aplicații"
    "aplicăm cunoștințele", "minitest", "temă de portofoliu", "mate practică", "test", "evaluare",
    "probleme propuse", "exerciții propuse", "exerciții și probleme", "exersăm", "știm să aplicăm",
    # the tests inside a chapter: scored items and their instructions
    "din oficiu", "timp de lucru", "notă. se acordă",
)
_SCORE = re.compile(r"^\d+\s+puncte?\b")
# A numbered item ("10. ...", "**3.** ...", Intuitext's "3 Determină ...") that asks for something is an exercise even
# outside a labelled box (the grade 6-8 books list them after the theory with no label); one that states something
# ("**2.** Orice triunghi echilateral este isoscel.") is a property and stays.
_WORKED_BOXES = ("problemă rezolvată", "probleme rezolvate", "exemplu", "exemple", "rezolvăm împreună",
                 "rezolvăm și observăm", "aplicație rezolvată", "exercițiu rezolvat", "exerciții rezolvate",
                 "situație-problemă")
_SOLUTION_LABELS = ("rezolvare", "soluție", "răspuns", "demonstrație")
_INLINE_LABEL = re.compile(r"^\s*\*\*[^\d*\s][^*]*\*\*")
_NUMBERED_ITEM = re.compile(r"^\s*(?:\*\*)?\d{1,2}(?:[.)])?(?:\*\*)?\s+(\S.*)$")
_TASK = re.compile(
    r"\b(calcula|calculeaz|determin|arăta|arată|demonstr|afla|află|desen|reprezent|construi|complet|scrie|scrieți|"
    r"stabil|verific|alege|încercui|compar|ordona|ordonează|rezolv|transform|găsi|precizați|explicați|identific|"
    r"justific|aproxim|estima|estimează|fie|știind|se consideră|se notează|în figura|observă|măsoară|efectua|"
    r"efectuează|aduce|adu|simplific|amplific|descompune|exprima|exprimă|preciz|se știe|folosind|arătați)",
    re.IGNORECASE)
_LABEL = re.compile(r"^\s*(?:#+\s*)?\*\*(.+?)\*\*\s*$")
_HEADING_TEXT = re.compile(r"^\s*#+\s*(.+?)\s*$")


def theory_only(markdown: str) -> str:
    """The page without its exercise boxes: a box runs from its label - bold, or a heading in some books - to the next
    label or heading."""
    kept: list[str] = []
    item: list[str] = []  # the numbered item being read: kept or dropped as a whole, when it ends
    dropping = False  # inside an exercise box, up to the next label or heading
    worked = False  # inside a worked problem or example: its numbered statement goes with its solution

    def close_item() -> None:
        # The task is often on the item's second line ("10. Punctul M este pe BC. ... / Aflați ..."): judge it whole.
        if item and (worked or _TASK.search(" ".join(item)) is None):
            kept.extend(item)
        item.clear()

    for line in markdown.splitlines():
        label = _LABEL.match(line) or _HEADING_TEXT.match(line)
        if label is not None:
            close_item()
            name = label.group(1).strip().strip("*").strip().rstrip("!?.:… ").lower()
            dropping = name.startswith(_EXERCISE_BOXES) or _SCORE.match(name) is not None
            if not name.startswith(_SOLUTION_LABELS):  # a "Rezolvare:" inside a worked problem keeps it worked
                worked = name.startswith(_WORKED_BOXES)
            if not dropping:
                kept.append(line)
            continue
        if dropping:
            continue
        if _NUMBERED_ITEM.match(line):
            close_item()
            item.append(line)
        elif _INLINE_LABEL.match(line):
            close_item()  # "**Observație.** Perimetrul ..." starts the next box
            kept.append(line)
        elif item:
            item.append(line)
        else:
            kept.append(line)
    close_item()
    return "\n".join(kept)


def documents_for(manual: ManualSpec, pages: list[Page]) -> list[Document]:
    """The pages of each lesson cut together (a section can run across its pages), all under the lesson's chapter;
    only their theory (see theory_only)."""
    documents: list[Document] = []
    pages = [Page(p.source_name, p.book_page, theory_only(p.markdown), p.figures) for p in pages]
    for lesson in manual.lessons:
        own = [p for p in pages if p.book_page is not None and lesson.first <= p.book_page <= lesson.last]
        if not own:
            continue
        cut, _ = chunk_pages(sorted(own, key=lambda p: p.book_page), home=lesson.code)
        # A numbered heading inside a lesson ("2. Adunarea") is the lesson's own numbering, not a section of the EN
        # chapter: the passage belongs to the chapter itself.
        documents += [d.model_copy(update={"metadata": {**d.metadata, "code": lesson.code,
                                                        "source_name": manual.name}}) for d in cut]
    return documents


def _with_figure_descriptions(markdown: str, figures: tuple) -> str:
    """There is no photo to cut a manual's figure from: its description stands in its place."""
    by_number = {f.number: f.description for f in figures}
    return _FIGURE.sub(lambda m: f"[figură: {by_number.get(int(m.group(1)), '').strip() or 'desen'}]", markdown)


def render(manual: ManualSpec, printed: int, max_side: int) -> bytes:
    document = pdfium.PdfDocument(str(manual.pdf))
    try:
        page = document[pdf_page_of(manual, printed) - 1]
        width, height = page.get_size()
        image = page.render(scale=max_side / max(width, height)).to_pil()
    finally:
        document.close()
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    jpeg, _, _ = prepare(buffer.getvalue(), max_side=max_side, quality=90)
    return jpeg


def transcribe(manual: ManualSpec, only: set[int] | None, max_usd: float, max_side: int, redo: bool) -> int:
    settings = get_settings()
    out_dir = OUT / manual.id
    out_dir.mkdir(parents=True, exist_ok=True)
    wanted = [p for p in manual.pages() if only is None or p in only]
    todo = [p for p in wanted if redo or not (out_dir / f"p{p:03d}.md").exists()]
    print(f"{manual.name}: {len(wanted)} pagini în hartă, {len(todo)} de transcris | plafon {max_usd:.2f} USD")
    spent = 0.0
    with httpx.Client(timeout=180) as client:
        for printed in todo:
            if spent >= max_usd:
                print(f"Plafon atins ({spent:.4f} USD): mă opresc înainte de pagina {printed}.")
                return 2
            try:
                result = transcribe_page(render(manual, printed, max_side), settings, client, MANUAL_INSTRUCTIONS)
            except TranscriptionError as error:
                print(f"  pagina {printed}: EROARE: {error}")
                return 1
            cost = cost_usd(result.input_tokens, result.output_tokens, settings)
            spent += cost
            text = _with_figure_descriptions(result.markdown, result.figures)
            (out_dir / f"p{printed:03d}.md").write_text(
                markdown_page(manual.name, pdf_page_of(manual, printed), printed, text, result.model, cost),
                encoding="utf-8")
            read = f" (modelul a citit {result.page_number})" if result.page_number not in (None, printed) else ""
            print(f"  pagina {printed} -> {manual.chapter_of(printed)} | {len(text)} caractere | ~{cost:.4f} USD{read}")
            for warning in result.warnings:
                print("    avertisment:", warning)
    print(f"Total: {spent:.4f} USD")
    return 0


def ingest(manual: ManualSpec, dry_run: bool) -> int:
    files = sorted((OUT / manual.id).glob("p*.md"))
    pages = [read_transcript(f) for f in files]
    missing = sorted(set(manual.pages()) - {p.book_page for p in pages})
    documents = documents_for(manual, pages)
    print(f"{manual.name}: {len(pages)} pagini transcrise -> {len(documents)} bucăți")
    if missing:
        print(f"  ATENȚIE: {len(missing)} pagini din hartă netranscrise încă: {missing[:12]}{' …' if len(missing) > 12 else ''}")
    by_code: dict[str, int] = {}
    for document in documents:
        by_code[document.metadata["code"]] = by_code.get(document.metadata["code"], 0) + 1
    print("  " + ", ".join(f"{code}: {n}" for code, n in sorted(by_code.items())))
    tokens = sum(len(tiktoken.get_encoding("cl100k_base").encode(d.page_content)) for d in documents)
    print(f"Embeddings: ~{tokens} tokeni, ~{tokens * EMBEDDING_PRICE_PER_MILLION / 1_000_000:.4f} USD")
    if dry_run:
        print("Dry-run: nu s-a scris nimic.")
        return 0
    settings = get_settings()
    with psycopg.connect(settings.psycopg_dsn) as conn:
        report = store_chunks(conn, documents, embeddings(settings), settings.embedding_model, manual.name,
                              source="MANUAL", whole_source=True)
        conn.commit()
    print(f"Scrise: {report.inserted} noi, {report.unchanged} neschimbate, {report.removed} șterse.")
    for code in report.unresolved:
        print(f"  fără capitol în aplicație pentru {code}, sărit")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Un manual întreg (clasele 5-8) în căutarea tutorelui.")
    sub = parser.add_subparsers(dest="command", required=True)
    one = sub.add_parser("transcribe", help="citește paginile din hartă cu modelul (se reia de unde a rămas)")
    one.add_argument("spec", type=pathlib.Path)
    one.add_argument("--pages", help="doar aceste pagini tipărite, ex. 104-106")
    one.add_argument("--max-usd", type=float, default=1.0)
    one.add_argument("--max-side", type=int, default=2000, help="latura lungă a imaginii, în px")
    one.add_argument("--redo", action="store_true", help="transcrie din nou și paginile deja făcute")
    two = sub.add_parser("ingest", help="taie paginile transcrise și le pune în pgvector")
    two.add_argument("spec", type=pathlib.Path)
    two.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv)

    manual = load_spec(args.spec)
    if args.command == "ingest":
        return ingest(manual, args.dry_run)
    from tutor.transcribe import parse_pages

    return transcribe(manual, parse_pages(args.pages), args.max_usd, args.max_side, args.redo)


if __name__ == "__main__":
    sys.exit(main())
