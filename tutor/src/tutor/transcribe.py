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
from google import genai
from google.genai import errors, types
from PIL import Image
from pypdf import PdfReader

from tutor.config import Settings, get_settings
from tutor.llm import gemini_access, using_openai

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
- Never put Markdown emphasis (** or _) inside a formula or inside \\text{...}; emphasis goes outside the $...$.
- Tables become Markdown tables.
- Where a figure, diagram, number line or drawing is printed, write [figura N] on its own line (N = 1, 2, ... in
  reading order) and list it in `figures` with a short Romanian description and a tight bounding box: x and y are
  the top-left corner, width and height the size, all as fractions (0 to 1) of the whole image's width and height.
  The box must contain the whole drawing with its labels, and nothing of the surrounding text. Do not describe the
  figure inside the text and do not try to reproduce it.
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
        "figures": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "number": {"type": "integer"},
                    "description": {"type": "string"},
                    "x": {"type": "number"},
                    "y": {"type": "number"},
                    "width": {"type": "number"},
                    "height": {"type": "number"},
                },
                "required": ["number", "description", "x", "y", "width", "height"],
            },
        },
        "warnings": {"type": "array", "items": {"type": "string"}},
    },
    "required": ["page_number", "markdown", "figures", "warnings"],
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
class Figure:
    """A drawing on the page: where it is, as fractions of the whole image (top-left corner and size)."""

    number: int
    description: str
    x: float
    y: float
    width: float
    height: float


@dataclass(frozen=True)
class Transcription:
    page_number: int | None
    markdown: str
    warnings: list[str]
    model: str
    input_tokens: int
    output_tokens: int
    figures: tuple[Figure, ...] = ()


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
    return transcription(data, response.get("model", fallback_model),
                         int(usage.get("input_tokens", 0)), int(usage.get("output_tokens", 0)))


def transcription(data: dict, model: str, input_tokens: int, output_tokens: int) -> Transcription:
    """The page as the model read it, whichever model that was."""
    figures = []
    for raw in data.get("figures", []):
        # A box is fractions of the image: keep it inside [0, 1] whatever the model answered.
        x, y = min(max(float(raw["x"]), 0.0), 1.0), min(max(float(raw["y"]), 0.0), 1.0)
        width, height = min(max(float(raw["width"]), 0.0), 1.0 - x), min(max(float(raw["height"]), 0.0), 1.0 - y)
        if width > 0 and height > 0:
            figures.append(Figure(int(raw["number"]), str(raw["description"]), x, y, width, height))
    return Transcription(
        page_number=data.get("page_number"),
        markdown=data.get("markdown", ""),
        warnings=list(data.get("warnings", [])),
        model=model,
        input_tokens=input_tokens,
        output_tokens=output_tokens,
        figures=tuple(figures),
    )


def gemini_client(settings: Settings) -> genai.Client:
    return genai.Client(vertexai=True, **gemini_access(settings))


def transcribe_with_gemini(jpeg: bytes, settings: Settings) -> Transcription:
    """One page through Gemini: the same instructions and JSON shape as the OpenAI request."""
    config = types.GenerateContentConfig(
        system_instruction=INSTRUCTIONS,
        max_output_tokens=MAX_OUTPUT_TOKENS,
        response_mime_type="application/json",
        response_json_schema=SCHEMA,
    )
    contents = [types.Part.from_bytes(data=jpeg, mime_type="image/jpeg"), "Transcribe this page."]
    try:
        client = gemini_client(settings)
    except (RuntimeError, OSError, ValueError) as error:
        raise TranscriptionError(str(error)) from error
    try:
        response = client.models.generate_content(model=settings.chat_model, contents=contents, config=config)
    except errors.APIError as error:
        raise TranscriptionError(f"Gemini a răspuns {error.code}: {str(error.message)[:300]}") from error
    finish = response.candidates[0].finish_reason if response.candidates else None
    if finish == types.FinishReason.MAX_TOKENS:
        raise TranscriptionError("Răspunsul a fost tăiat (prea lung); încearcă o pagină mai mică.")
    if not response.text:
        raise TranscriptionError("Răspunsul nu conține text.")
    try:
        data = json.loads(response.text)
    except json.JSONDecodeError as error:
        raise TranscriptionError("Răspunsul nu e JSON-ul așteptat.") from error
    usage = response.usage_metadata
    return transcription(data, settings.chat_model,
                         (usage.prompt_token_count or 0) if usage else 0,
                         (usage.candidates_token_count or 0) if usage else 0)


def transcribe_page(jpeg: bytes, settings: Settings, client: httpx.Client) -> Transcription:
    if not using_openai(settings):
        return transcribe_with_gemini(jpeg, settings)
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
    ]
    if result.figures:
        header.append("figures: " + json.dumps([f.__dict__ for f in result.figures], ensure_ascii=False))
    header += ["---", ""]
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
    parser.add_argument("--dry-run", action="store_true", help="doar pregătește imaginile, fără apel către model")
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
