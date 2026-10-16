"""Cuts transcribed textbook pages into chunks, each tagged with the LMS chapter page it belongs to.

A page is split at its numbered headings ("## 2. Adunarea", "### 2.2. SCĂDEREA"); the number plus the chapter
(from the heading "I. MULȚIMEA ..." or from the printed page number) gives the code of the LMS page, for example
AIS2P2. Text before the first heading of a page continues the section the previous page ended in.
"""

import hashlib
import json
import pathlib
import re
from dataclasses import dataclass

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter

from tutor import page_map

MAX_CHARS = 1400  # about 450 tokens: one idea with its formulas, small enough to rank well
MIN_CHARS = 200  # a smaller tail is merged into the chunk before it

_HEADING = re.compile(r"^(#{1,6})\s+(.*\S)\s*$")
# "2. Adunarea", "2.2. SCĂDEREA", but also "1.1.AXA NUMERELOR" and "8.Mulțimi" (the photos often have no space after
# the dot); "7) Criteriul ..." is a list item, not a section.
_NUMBERED = re.compile(r"^(\d+)(?:\.(\d+))?(?:\.|\s)\s*(\S.*)$")
_EXAMPLE = re.compile(r"^\W*(ex\b|ex:|exemplu|exemple|exerci)", re.IGNORECASE)


@dataclass(frozen=True)
class Page:
    source_name: str
    book_page: int | None
    markdown: str
    # The drawings the transcription found: number, description and box (fractions of the photo).
    figures: tuple[dict, ...] = ()


@dataclass
class _Block:
    code: str | None
    lines: list[str]


_TEXT_IN_MATH = re.compile(r"\\text\{([^{}]*)\}")


def clean_math_markdown(text: str) -> str:
    """The model sometimes puts Markdown emphasis inside a formula's \\text{...}, which KaTeX cannot read."""
    return _TEXT_IN_MATH.sub(lambda m: "\\text{" + re.sub(r"[*_]+", "", m.group(1)) + "}", text)


def parse_transcript(text: str, default_source: str) -> Page:
    """A transcript as tutor.transcribe writes it: a small front matter, then the Markdown."""
    meta: dict[str, str] = {}
    body = text
    if text.startswith("---"):
        _, header, body = text.split("---", 2)
        for line in header.strip().splitlines():
            key, _, value = line.partition(":")
            meta[key.strip()] = value.strip()
    page = meta.get("book_page", "")
    try:
        figures = tuple(json.loads(meta["figures"])) if "figures" in meta else ()
    except json.JSONDecodeError:
        figures = ()
    return Page(meta.get("source_pdf", default_source), int(page) if page.isdigit() else None,
                clean_math_markdown(body.strip()), figures)


_FIGURE_PLACEHOLDER = re.compile(r"\[figura\s+(\d+)\]", re.IGNORECASE)


def figure_token(photo_id: int, figure: dict, padding: float = 0.012) -> str:
    """![description](poza:ID?x=&y=&w=&h=): the app draws that part of the photo where the figure belongs."""
    x = max(0.0, figure["x"] - padding)
    y = max(0.0, figure["y"] - padding)
    width = min(1.0 - x, figure["width"] + 2 * padding)
    height = min(1.0 - y, figure["height"] + 2 * padding)
    alt = re.sub(r"[\[\]()\n]", " ", figure.get("description", "")).strip() or "figură"
    return f"![{alt}](poza:{photo_id}?x={x:.3f}&y={y:.3f}&w={width:.3f}&h={height:.3f})"


def link_figures(page: Page, photo_id: int, padding: float = 0.012) -> Page:
    """Replaces each [figura N] of a page transcribed from a photo with the part of that photo showing the figure."""
    by_number = {int(f["number"]): f for f in page.figures}

    def replace(match: re.Match) -> str:
        figure = by_number.get(int(match.group(1)))
        return figure_token(photo_id, figure, padding) if figure else "[figură]"

    return Page(page.source_name, page.book_page, _FIGURE_PLACEHOLDER.sub(replace, page.markdown), page.figures)


def read_transcript(path: pathlib.Path) -> Page:
    """A pNN.md written by tutor.transcribe."""
    return parse_transcript(path.read_text(encoding="utf-8"), path.parent.name)


def content_hash(text: str) -> str:
    return hashlib.sha256(text.strip().encode("utf-8")).hexdigest()


def is_example(text: str) -> bool:
    """Worked examples and exercises start with 'Ex:', 'Exemplu', 'Exerciții' (or sit under such a heading)."""
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    headings = [re.sub(r"^#+\s*(\d+(\.\d+)*\.?\s+)?", "", line) for line in lines if line.startswith("#")]
    body = [line for line in lines if not line.startswith("#")]
    if any(_EXAMPLE.match(heading) for heading in headings):
        return True
    return bool(body) and _EXAMPLE.match(body[0]) is not None


def chunk_pages(pages: list[Page], max_chars: int = MAX_CHARS, min_chars: int = MIN_CHARS) -> tuple[list[Document], list[str]]:
    """Returns the chunks (LangChain Documents) and a list of human-readable problems (skipped pages)."""
    problems: list[str] = []
    documents: list[Document] = []
    state_chapter: str | None = None
    state_section: int | None = None
    state_point: int | None = None
    previous_page: int | None = None

    numbered = sorted((p for p in pages if p.book_page is not None), key=lambda p: p.book_page)
    for page in pages:
        if page.book_page is None:
            problems.append(f"{page.source_name}: pagină fără număr citit din imagine, sărită")

    for page in numbered:
        chapter = page_map.chapter_for_page(page.book_page)
        continuing = previous_page is not None and page.book_page == previous_page + 1
        if not continuing:
            state_chapter, state_section, state_point = None, None, None
        chapter = chapter or state_chapter  # a page outside the Cuprins continues the previous one, if any
        if state_chapter is not None and chapter != state_chapter and chapter is not None:
            state_section = state_point = None  # a new chapter by page number: restart the section
        previous_page = page.book_page

        blocks: list[_Block] = []

        def open_block() -> None:
            # A block that is only headings so far (a chapter title right before its first section) is not a
            # chunk of its own: its headings move to the front of the next block, which has the body.
            carried: list[str] = []
            if blocks and all(not text.strip() or _HEADING.match(text) for text in blocks[-1].lines):
                carried = blocks.pop().lines
            code = page_map.section_code(chapter, state_section, state_point) if chapter else None
            blocks.append(_Block(code, carried))

        open_block()
        for line in page.markdown.splitlines():
            heading = _HEADING.match(line)
            if heading is not None:
                title = heading.group(2)
                from_heading = page_map.chapter_from_heading(title, page.book_page)
                if from_heading is not None:
                    chapter, state_section, state_point = from_heading, None, None
                    open_block()
                    blocks[-1].lines.append(line)
                    continue
                match = _NUMBERED.match(title)
                if match is not None and chapter is not None:
                    number = int(match.group(1))
                    point = int(match.group(2)) if match.group(2) else None
                    # A numbered list item that came out as a heading ("7. Criteriul de divizibilitate cu 10" inside
                    # section 7.3) is text, not a new section: a section number only moves forward.
                    if _moves_forward(state_section, state_point, number, point):
                        state_section, state_point = number, point
                        open_block()
                        blocks[-1].lines.append(line)
                        continue
            blocks[-1].lines.append(line)
        state_chapter = chapter

        position = 0
        for block in blocks:
            text = "\n".join(block.lines).strip()
            if not text:
                continue
            if block.code is None:
                problems.append(f"{page.source_name} p.{page.book_page}: text fără capitol, sărit")
                continue
            for piece in _split(text, max_chars, min_chars):
                position += 1
                documents.append(Document(page_content=piece, metadata={
                    "code": block.code,
                    "kind": "EXAMPLE" if is_example(piece) else "THEORY",
                    "book_page": page.book_page,
                    "source_name": page.source_name,
                    "position": position,
                }))
    return documents, problems


def _moves_forward(section: int | None, point: int | None, number: int, new_point: int | None) -> bool:
    """Does heading `number[.new_point]` come after where we are (`section[.point]`) in the chapter?"""
    if section is None or number > section:
        return True
    if number < section or new_point is None:
        return False
    return point is None or new_point > point


def _split(text: str, max_chars: int, min_chars: int) -> list[str]:
    """Whole paragraphs (a table or a formula block stays in one piece) packed up to max_chars."""
    paragraphs = [p.strip() for p in re.split(r"\n\s*\n", text) if p.strip()]
    splitter = RecursiveCharacterTextSplitter(chunk_size=max_chars, chunk_overlap=80,
                                              separators=["\n", "; ", ". ", " "])
    pieces: list[str] = []
    current = ""
    for paragraph in paragraphs:
        candidates = [paragraph] if len(paragraph) <= max_chars else splitter.split_text(paragraph)
        for candidate in candidates:
            if current and len(current) + 2 + len(candidate) > max_chars:
                pieces.append(current)
                current = candidate
            else:
                current = f"{current}\n\n{candidate}" if current else candidate
    if current:
        if pieces and len(current) < min_chars:
            pieces[-1] = f"{pieces[-1]}\n\n{current}"
        else:
            pieces.append(current)
    return pieces
