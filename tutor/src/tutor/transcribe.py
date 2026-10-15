"""Reads a PDF of photographed textbook pages and transcribes each page to Markdown + LaTeX with a vision model.

Run:  uv run python -m tutor.transcribe "data/pdf/I. MULTIMEA.pdf" --max-usd 0.10

One request per page (so the cost of each page is known), the answer is strict JSON, and nothing is stored by the
provider (`store: false`). Results are written to data/theory/<pdf name>/pNN.md, which is gitignored.
"""

import argparse
import base64
import io
import json
import pathlib
import re
import sys
from dataclasses import dataclass

import httpx
from PIL import Image
from pypdf import PdfReader

from tutor.config import Settings, get_settings

INSTRUCTIONS = """\
You transcribe ONE photographed page of a Romanian school mathematics textbook (grade 8) into Markdown.

Rules:
- Write the text exactly as printed, in Romanian, with diacritics. Do not summarise, translate, correct or add anything.
- Keep the structure with a fixed heading depth: the chapter title (for example 'I. MULȚIMEA NUMERELOR NATURALE') is
  '#'; a section with ONE number ('3. Înmulțirea numerelor naturale') is '##'; a subsection with TWO numbers
  ('3.1. Înmulțirea') is '###'. Keep the printed numbers. A page that starts mid-section simply starts with its
  first heading. Labels such as 'Reține!' or 'Atenție!' stay as bold labels.
- Mathematics goes in LaTeX between $...$ (inline) or $$...$$ (display): fractions \\frac{a}{b}, powers a^{n},
  roots \\sqrt{x}, \\cdot for multiplication, \\neq, \\leq. A printed dot between numbers is multiplication, a dash is
  subtraction: never swap them. Number sets printed in blackboard bold are \\mathbb{N}, \\mathbb{Z}, \\mathbb{Q},
  \\mathbb{R}; with a star (non-zero elements) \\mathbb{N}^*.
- Tables become Markdown tables.
- Where a figure or diagram is printed, write [figură: short description]; do not try to reproduce it.
- IGNORE the diagonal watermark "UNIVERSUL MATEMATIC by Bogdan Oltianu" and any faint text that shows through from
  the other side of the page.
- If something is unreadable write [?] instead of guessing.
- page_number is the printed page number (often circled in a bottom corner), or null if you cannot see one.
"""

MAX_OUTPUT_TOKENS = 8_000

SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "page_number": {"type": ["integer", "null"]},
        "markdown": {"type": "string"},
        "warnings": {"type": "array", "items": {"type": "string"}},
    },
    "required": ["page_number", "markdown", "warnings"],
}


class TranscriptionError(Exception):
    """The provider could not give a usable transcription (message is safe to show: it never holds the key)."""


@dataclass(frozen=True)
class PageImage:
    pdf_page: int  # 1-based position inside the PDF
    jpeg: bytes
    width: int
    height: int


@dataclass(frozen=True)
class Transcription:
    page_number: int | None
    markdown: str
    warnings: list[str]
    model: str
    input_tokens: int
    output_tokens: int


def cost_usd(input_tokens: int, output_tokens: int, settings: Settings) -> float:
    return (input_tokens * settings.price_in_per_million + output_tokens * settings.price_out_per_million) / 1_000_000


def prepare(image_bytes: bytes, max_side: int = 1800, quality: int = 85) -> tuple[bytes, int, int]:
    """Shrinks a photo so its long edge is at most max_side px and re-encodes it as JPEG (fewer image tokens)."""
    with Image.open(io.BytesIO(image_bytes)) as source:
        image = source.convert("RGB")
    scale = min(1.0, max_side / max(image.size))
    if scale < 1.0:
        image = image.resize((round(image.width * scale), round(image.height * scale)), Image.Resampling.LANCZOS)
    out = io.BytesIO()
    image.save(out, format="JPEG", quality=quality)
    return out.getvalue(), image.width, image.height


def pdf_pages(path: pathlib.Path, max_side: int, only: set[int] | None = None) -> list[PageImage]:
    """The largest embedded image of each PDF page (scanned/photographed pages are one image per page)."""
    reader = PdfReader(str(path))
    pages: list[PageImage] = []
    for number, page in enumerate(reader.pages, start=1):
        if only is not None and number not in only:
            continue
        images = list(page.images)
        if not images:
            raise TranscriptionError(f"Pagina {number} din PDF nu conține o imagine (PDF cu text, nu cu poze?).")
        biggest = max(images, key=lambda image: len(image.data))
        jpeg, width, height = prepare(biggest.data, max_side=max_side)
        pages.append(PageImage(number, jpeg, width, height))
    return pages


def build_request(jpeg: bytes, settings: Settings) -> dict:
    body: dict = {
        "model": settings.chat_model,
        "instructions": INSTRUCTIONS,
        "store": False,
        "max_output_tokens": MAX_OUTPUT_TOKENS,
        "input": [{
            "role": "user",
            "content": [
                {"type": "input_text", "text": "Transcribe this page."},
                {
                    "type": "input_image",
                    "detail": settings.image_detail,
                    "image_url": "data:image/jpeg;base64," + base64.b64encode(jpeg).decode("ascii"),
                },
            ],
        }],
        "text": {"format": {"type": "json_schema", "name": "page_transcription", "strict": True, "schema": SCHEMA}},
    }
    if settings.reasoning_effort:
        body["reasoning"] = {"effort": settings.reasoning_effort}
    return body


def parse_response(response: dict, fallback_model: str) -> Transcription:
    if response.get("status") == "incomplete":
        raise TranscriptionError("Răspunsul a fost tăiat (prea lung); încearcă o pagină mai mică.")
    text = None
    for item in response.get("output", []):
        if item.get("type") != "message":
            continue  # reasoning items
        for part in item.get("content", []):
            if part.get("type") == "refusal":
                raise TranscriptionError("Modelul a refuzat pagina.")
            if part.get("type") == "output_text":
                text = part.get("text")
    if text is None:
        raise TranscriptionError("Răspunsul nu conține text.")
    try:
        data = json.loads(text)
    except json.JSONDecodeError as error:
        raise TranscriptionError("Răspunsul nu e JSON-ul așteptat.") from error
    usage = response.get("usage", {})
    return Transcription(
        page_number=data.get("page_number"),
        markdown=data.get("markdown", ""),
        warnings=list(data.get("warnings", [])),
        model=response.get("model", fallback_model),
        input_tokens=int(usage.get("input_tokens", 0)),
        output_tokens=int(usage.get("output_tokens", 0)),
    )


def transcribe_page(jpeg: bytes, settings: Settings, client: httpx.Client) -> Transcription:
    if settings.openai_api_key is None:
        raise TranscriptionError("Lipsește OPENAI_API_KEY (pune-l în tutor/.env).")
    try:
        response = client.post(
            f"{settings.openai_base_url}/responses",
            headers={"Authorization": f"Bearer {settings.openai_api_key.get_secret_value()}"},
            json=build_request(jpeg, settings),
        )
    except httpx.HTTPError as error:
        raise TranscriptionError(f"OpenAI nu a putut fi contactat ({type(error).__name__}).") from error
    if response.status_code in (401, 403):
        raise TranscriptionError("Cheia OpenAI e respinsă (greșită sau fără drepturi).")
    if response.status_code == 429:
        raise TranscriptionError("Limită de cereri sau credit epuizat (429).")
    if response.status_code >= 400:
        # The body explains a 400 (unknown model, bad image); it never contains the key.
        raise TranscriptionError(f"OpenAI a răspuns {response.status_code}: {response.text[:300]}")
    return parse_response(response.json(), settings.chat_model)


def render_markdown(pdf_name: str, page: PageImage, result: Transcription, cost: float) -> str:
    header = [
        "---",
        f"source_pdf: {pdf_name}",
        f"pdf_page: {page.pdf_page}",
        f"book_page: {result.page_number if result.page_number is not None else 'unknown'}",
        f"model: {result.model}",
        f"tokens_in: {result.input_tokens}",
        f"tokens_out: {result.output_tokens}",
        f"cost_usd: {cost:.5f}",
        "---",
        "",
    ]
    warnings = [f"<!-- warning: {w} -->" for w in result.warnings]
    return "\n".join(header + warnings + [result.markdown.strip(), ""])


def parse_pages(spec: str | None) -> set[int] | None:
    if not spec:
        return None
    pages: set[int] = set()
    for part in spec.split(","):
        match = re.fullmatch(r"\s*(\d+)(?:\s*-\s*(\d+))?\s*", part)
        if not match:
            raise ValueError(f"pagini invalide: {spec!r} (exemplu: 1-3,5)")
        first, last = int(match.group(1)), int(match.group(2) or match.group(1))
        pages.update(range(first, last + 1))
    return pages


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Transcrie paginile dintr-un PDF cu poze în Markdown + LaTeX.")
    parser.add_argument("pdf", type=pathlib.Path)
    parser.add_argument("--pages", help="ex. 1-2 sau 1,3 (implicit: toate)")
    parser.add_argument("--max-side", type=int, default=1800, help="latura lungă a imaginii trimise, în px")
    parser.add_argument("--max-usd", type=float, default=0.25, help="se oprește când costul estimat depășește plafonul")
    parser.add_argument("--dry-run", action="store_true", help="doar pregătește imaginile, fără apel către OpenAI")
    parser.add_argument("--out", type=pathlib.Path, default=pathlib.Path("data/theory"))
    args = parser.parse_args(argv)

    settings = get_settings()
    try:
        pages = pdf_pages(args.pdf, args.max_side, parse_pages(args.pages))
    except (TranscriptionError, ValueError, OSError) as error:
        print("Eroare:", error)
        return 1

    print(f"{args.pdf.name}: {len(pages)} pagini | model {settings.chat_model} | plafon {args.max_usd:.2f} USD")
    for page in pages:
        print(f"  pagina PDF {page.pdf_page}: {page.width}x{page.height} px, {len(page.jpeg) / 1024:.0f} KB de trimis")
    if args.dry_run:
        print("Dry-run: nu s-a trimis nimic.")
        return 0

    out_dir = args.out / args.pdf.stem.strip().replace(" ", "_")
    out_dir.mkdir(parents=True, exist_ok=True)
    spent = 0.0
    with httpx.Client(timeout=180) as client:
        for page in pages:
            if spent >= args.max_usd:
                print(f"Plafon atins ({spent:.4f} USD): mă opresc înainte de pagina {page.pdf_page}.")
                break
            try:
                result = transcribe_page(page.jpeg, settings, client)
            except TranscriptionError as error:
                print(f"  pagina PDF {page.pdf_page}: EROARE: {error}")
                return 1
            cost = cost_usd(result.input_tokens, result.output_tokens, settings)
            spent += cost
            target = out_dir / f"p{page.pdf_page:02d}.md"
            target.write_text(render_markdown(args.pdf.name, page, result, cost), encoding="utf-8")
            print(f"  pagina PDF {page.pdf_page}: pagina din carte {result.page_number} | "
                  f"{result.input_tokens} tokeni in, {result.output_tokens} out | ~{cost:.4f} USD | {target}")
            for warning in result.warnings:
                print("    avertisment:", warning)
    print(f"Total estimat: {spent:.4f} USD")
    return 0


if __name__ == "__main__":
    sys.exit(main())
