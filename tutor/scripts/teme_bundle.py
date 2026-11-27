"""„Teme recapitulative": packs the solved exercises into the zip the app imports (Culegere -> „Încarcă culegerea”).

No model is called. For each of the 387 exercises it writes the statement without its "[figura N]" lines, split into its
requirements a), b)..., the first solving run as the barem (cut per requirement where the run says "Pentru punctul a)"),
the answer of each requirement, and - only where both runs agree on a value that is a plain number - the result the
student types. The drawing is cut from the scanned page with the boxes of scripts/teme_figure_boxes.py (several drawings
of one exercise are stacked). The verdict of the two runs goes along: the app shows everything but AGREE as „de
verificat”, and nothing reaches a student before the teacher approves it.

The zip holds book content: it stays in tutor/data/teme/ (git-ignored) and is uploaded by hand.

    uv run --with pymupdf --with sympy python -X utf8 scripts/teme_bundle.py
"""

import io
import json
import pathlib
import re
import zipfile

import fitz
from PIL import Image

from tutor.answer_check import clean_statement, label_key, repair_latex, repair_page_latex, same, to_expr

import teme_figure_boxes as boxes_script

DATA = boxes_script.DATA
OUT = DATA / "teme-culegere.zip"
CROPS = DATA / "crops"
MAX_PARTS = 6  # QuizItemPart.MAX_PARTS: an exercise with more requirements stays one block of text
PAD = 8  # per mille of the page around each drawing: the boxes are tight and letters sit on their edge
# An exercise that is only "a) ... b) ..." has no common text; a test's problem needs one.
EMPTY_STEM = "Rezolvați:"
REQUIREMENT = re.compile(r"(?m)^([a-h])\)\s*")
PART_MARK = re.compile(r"^\s*(?:Pentru\s+)?(?:punctul|subpunctul|cerința)\s*([a-h])\)?\s*[:.)]?\s*$", re.IGNORECASE)


def split(statement: str) -> tuple[str, list[tuple[str, str]]]:
    """The common text and the requirements [(label, text)]; no requirements when there are none or too many."""
    marks = list(REQUIREMENT.finditer(statement))
    if not marks or len(marks) > MAX_PARTS:
        return statement, []
    labels = [m.group(1) for m in marks]
    if labels != [chr(ord("a") + i) for i in range(len(labels))]:
        return statement, []  # a), c) without b): not requirements we can trust
    stem = statement[:marks[0].start()].strip()
    parts = []
    for i, m in enumerate(marks):
        end = marks[i + 1].start() if i + 1 < len(marks) else len(statement)
        parts.append((m.group(1), statement[m.end():end].strip()))
    return stem, parts


def steps_by_part(steps: list[str]) -> tuple[list[str], dict[str, list[str]]]:
    """The steps before the first "Pentru punctul a):" and the steps of each requirement."""
    common: list[str] = []
    per: dict[str, list[str]] = {}
    current = None
    for step in steps:
        mark = PART_MARK.match(step)
        if mark:
            current = mark.group(1).lower()
            per.setdefault(current, [])
            continue
        (per[current] if current else common).append(step)
    return common, per


def number_text(value: str | None) -> str | None:
    """A SymPy value written the way a student types it, when it is a plain number (12, -3/4); else None."""
    expr = to_expr(value) if value else None
    if expr is None or not getattr(expr, "is_Rational", False):
        return None
    return str(expr.p) if expr.q == 1 else f"{expr.p}/{expr.q}"


def value(answer: dict | None) -> str | None:
    if not answer or answer.get("sympy") in (None, "", "null", "None"):
        return None
    return answer["sympy"]


def text_of(answer: dict | None) -> str | None:
    return None if not answer else (answer.get("text") or "").strip() or None


def crop(book_page: int, page_boxes: list[list[int]], pages, cache: dict) -> list[Image.Image]:
    if not page_boxes:
        return []
    if book_page not in cache:
        cache[book_page] = Image.open(io.BytesIO(boxes_script.render(book_page, pages))).convert("RGB")
    image = cache[book_page]
    w, h = image.size
    out = []
    for ymin, xmin, ymax, xmax in page_boxes:
        box = (max(0, (xmin - PAD) * w // 1000), max(0, (ymin - PAD) * h // 1000),
               min(w, (xmax + PAD) * w // 1000), min(h, (ymax + PAD) * h // 1000))
        out.append(image.crop(box))
    return out


def stack(images: list[Image.Image]) -> bytes:
    gap = 24
    width = max(i.width for i in images)
    height = sum(i.height for i in images) + gap * (len(images) - 1)
    sheet = Image.new("RGB", (width, height), "white")
    y = 0
    for i in images:
        sheet.paste(i, ((width - i.width) // 2, y))
        y += i.height + gap
    buffer = io.BytesIO()
    sheet.save(buffer, "JPEG", quality=85)
    return buffer.getvalue()


def main() -> None:
    exercises = json.loads((DATA / "exercises.json").read_text(encoding="utf-8"))
    solutions = {}
    for line in (DATA / "solutions.jsonl").read_text(encoding="utf-8").splitlines():
        row = repair_latex(json.loads(line))
        solutions[row["id"]] = row
    figure_boxes = json.loads(boxes_script.BOXES.read_text(encoding="utf-8")) if boxes_script.BOXES.exists() else {}
    pages = boxes_script.pdf_pages()
    rendered: dict = {}
    CROPS.mkdir(exist_ok=True)
    out_rows = []
    figures: dict[str, bytes] = {}
    for ex in exercises:
        solved = solutions.get(ex["id"], {"status": "UNPARSED", "runs": []})
        runs = [r for r in solved.get("runs", []) if "error" not in r]
        first = runs[0] if runs else {"steps": [], "answers": []}
        second = runs[1] if len(runs) > 1 else {"answers": []}
        statement = clean_statement(repair_page_latex(ex["statement"]))
        stem, parts = split(statement)
        common, per_part = steps_by_part(first.get("steps", []))
        one = {label_key(a["label"]): a for a in first.get("answers", [])}
        two = {label_key(a["label"]): a for a in second.get("answers", [])}

        def checked(label: str | None) -> tuple[str | None, str | None, bool]:
            """(answer text, typed result, both runs agree) for one requirement, or for the whole exercise."""
            if label is None:
                a = first.get("answers", [])
                b = second.get("answers", [])
                a1, b1 = (a[0] if len(a) == 1 else None), (b[0] if len(b) == 1 else None)
                text = "; ".join(t for t in (text_of(x) for x in a) if t) or None
            else:
                a1, b1 = one.get(label), two.get(label)
                text = text_of(a1)
            agree = solved["status"] == "AGREE" or bool(same(value(a1), value(b1)))
            return text, number_text(value(a1)) if agree else None, agree

        rows = []
        for label, text in parts:
            answer, result, agree = checked(label)
            steps = per_part.get(label, [])
            rows.append({"label": label, "statement": text, "solution": "\n".join(steps) or None,
                         "answer": answer, "result": result, "verified": agree})
        whole_answer, whole_result, whole_agree = checked(None) if not parts else (None, None, False)
        solution_steps = common if parts and per_part else first.get("steps", [])
        figure = None
        wants_figure = boxes_script.needs_figure(ex)
        if wants_figure:
            found = []
            by_key = figure_boxes.get("keys", {}).get(ex["id"])
            if by_key:  # found by the figure number the text cites (teme_figure_boxes.py --keys)
                found = crop(by_key["page"], by_key["boxes"], pages, rendered)
            for page in ex["pages"] if not found else []:
                found += crop(page, figure_boxes.get(str(page), {}).get(str(ex["number"]), []), pages, rendered)
            if not found:  # printed at the top of the next page (see teme_figure_boxes.on_next_page)
                after = max(ex["pages"]) + 1
                found = crop(after, figure_boxes.get("next", {}).get(str(after), {}).get(str(ex["number"]), []),
                             pages, rendered)
            if found:
                figure = f"figures/{ex['id']}.jpg"
                figures[figure] = stack(found)
                (CROPS / f"{ex['id']}.jpg").write_bytes(figures[figure])
        out_rows.append({
            "key": ex["id"], "tema": ex["tema"], "number": ex["number"], "statement": (stem or EMPTY_STEM) if parts else statement,
            "solution": "\n".join(solution_steps) or None, "answer": whole_answer, "result": whole_result,
            "verified": whole_agree if not parts else all(p["verified"] for p in rows),
            "aiStatus": solved["status"], "parts": rows, "figure": figure,
            "figureMissing": wants_figure and figure is None,
        })
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("culegere.json", json.dumps({"source": "Teme recapitulative", "exercises": out_rows},
                                                ensure_ascii=False, indent=1))
        for name, data in figures.items():
            zf.writestr(name, data)
    missing = [r["key"] for r in out_rows if r["figureMissing"]]
    print(f"{len(out_rows)} exerciții, {len(figures)} figuri, fără figură găsită: {len(missing)} {missing}")
    print(f"cu rezultat de tastat: {sum(1 for r in out_rows for p in (r['parts'] or [r]) if p['result'])}")
    print("scris:", OUT, f"{OUT.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
