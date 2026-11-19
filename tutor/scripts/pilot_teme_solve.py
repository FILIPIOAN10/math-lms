"""Pilot, step 3: solves every extracted exercise TWICE, independently, and compares the final answers.

Each solve is one call to the strong model asking for JSON: the steps, and the final answers both as text and as a
SymPy expression. Two answers agree when SymPy says their difference is zero (or the sets are equal); a proof ("Arătați
că ...") cannot be compared that way and is only marked PROOF for the teacher. An exercise whose statement mentions a
figure is sent with the page image, because the statement alone does not say what the figure shows.

    uv run --with pymupdf --with sympy python -X utf8 scripts/pilot_teme_solve.py [--limit 40] [--ids T4-3,T4-4]
"""

import argparse
import collections
import concurrent.futures
import glob
import json
import pathlib
import re
import time

import fitz
from google.genai import errors, types

from tutor.config import Settings
from tutor.transcribe import gemini_client, prepare

DATA = pathlib.Path(__file__).resolve().parent.parent / "data" / "teme"
SOURCES = r"C:\Users\Filip\Desktop\Teste\Invata_cu_succes\TEME RECAPITULATIVE_*.pdf"
MODEL = "gemini-3.1-pro-preview"
# When the strong preview model answers empty (it does under load), this one stands in for that solve, so the exercise
# is still solved twice - and by two different models, which is an even better check than the same model twice.
FALLBACK_MODEL = "gemini-3.8-flash"

SYSTEM = """\
You are an exact mathematics solver for the Romanian grade-8 curriculum (Evaluarea Națională). Solve the exercise \
completely, using only methods a grade-8 student knows. Write the steps in Romanian, short and numbered, with LaTeX \
between dollar signs. Never skip a step that changes the value.

Give the final answers separately for each requirement (a, b, ...; use "" when the exercise has one question). For \
every answer give "text" (as the student would write it, LaTeX allowed) and "sympy": the same value as a SymPy \
expression using only numbers, + - * / ** sqrt() Abs() pi oo Rational(a,b) Interval(a,b) FiniteSet(...) Union(...), \
or null when the answer is not a number, an expression or a set (a proof, a description). A proof ("Arătați că ...", \
"Demonstrați că ...") has kind PROOF and no sympy.

If the exercise depends on a figure you cannot see, or the statement is unreadable, set needs_figure to true and do \
not guess. If you are not sure of the result, set unsure to true.
"""

SCHEMA = {
    "type": "object",
    "properties": {
        "kind": {"type": "string", "enum": ["NUMERIC", "EXPRESSION", "SET", "PROOF", "OTHER"]},
        "steps": {"type": "array", "items": {"type": "string"}},
        "answers": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {"label": {"type": "string"}, "text": {"type": "string"}, "sympy": {"type": ["string", "null"]}},
                "required": ["label", "text", "sympy"],
            },
        },
        "needs_figure": {"type": "boolean"},
        "unsure": {"type": "boolean"},
    },
    "required": ["kind", "steps", "answers", "needs_figure", "unsure"],
}

SAFE = re.compile(r"^[0-9A-Za-z_+\-*/^().,\s]+$")


def page_images() -> dict[int, bytes]:
    """Book page number -> the rendered page (only what a figure exercise needs), read from the transcript headers."""
    sources = []
    for path in sorted(glob.glob(SOURCES)):
        doc = fitz.open(path)
        sources.extend((doc, i) for i in range(len(doc)))
    images = {}
    for order, (doc, index) in enumerate(sources, start=1):
        transcript = DATA / f"page-{order:02d}.md"
        match = re.search(r"^book_page:\s*(\d+)", transcript.read_text(encoding="utf-8"), re.MULTILINE) if transcript.exists() else None
        if match:
            page = doc[index]
            zoom = 1800 / max(page.rect.width, page.rect.height)
            images[int(match.group(1))] = prepare(page.get_pixmap(matrix=fitz.Matrix(zoom, zoom)).tobytes("jpeg"), 1800)[0]
    return images


def solve_once(exercise: dict, images: dict[int, bytes], temperature: float, model: str = MODEL,
               attempts: int = 2) -> dict:
    parts: list = []
    if exercise["has_figure"]:
        for page in exercise["pages"]:
            if page in images:
                parts.append(types.Part.from_bytes(data=images[page], mime_type="image/jpeg"))
    parts.append(f"Exercise {exercise['number']}:\n{exercise['statement']}")
    config = types.GenerateContentConfig(
        system_instruction=SYSTEM, response_mime_type="application/json", response_json_schema=SCHEMA,
        max_output_tokens=24_000, temperature=temperature,
        thinking_config=types.ThinkingConfig(thinking_level=types.ThinkingLevel.HIGH))
    client = gemini_client(Settings())
    data = None
    for attempt in range(attempts):  # a preview model sometimes answers empty: a short wait, then one more try
        if attempt:
            time.sleep(4 * 3 ** (attempt - 1))  # 4, 12, 36, 108 s
        try:
            response = client.models.generate_content(model=model, contents=parts, config=config)
        except errors.APIError as error:
            return {"error": f"{error.code}: {str(error.message)[:120]}"}
        if not response.text:
            continue
        try:
            data = json.loads(response.text)
            break
        except json.JSONDecodeError:
            continue
    if data is None:
        return {"error": "empty"}
    usage = response.usage_metadata
    data["tokens"] = [(usage.prompt_token_count or 0) if usage else 0, (usage.candidates_token_count or 0) if usage else 0,
                      (getattr(usage, "thoughts_token_count", 0) or 0) if usage else 0]
    return data


def to_expr(text: str | None):
    import sympy
    allowed = ("sqrt", "Abs", "pi", "oo", "Rational", "Interval", "FiniteSet", "Union")
    if not text or not SAFE.match(text) or "__" in text:
        return None
    if any(name not in allowed for name in re.findall(r"[A-Za-z_]\w*", text)):
        return None  # only the listed names: the string is evaluated, so nothing else may be named
    names = {n: getattr(sympy, n) for n in allowed}
    scope: dict = {}
    exec("from sympy import Integer, Float, Rational, Symbol", scope)  # what the number transformations need
    scope.update(names)
    try:
        return sympy.parse_expr(text.replace("^", "**"), local_dict=names, global_dict=scope)
    except Exception:  # noqa: BLE001 - any parse problem means "not comparable"
        return None


def same(a: str | None, b: str | None) -> bool | None:
    """True/False when both answers can be compared as math, None when one of them is not an expression."""
    import sympy
    x, y = to_expr(a), to_expr(b)
    if x is None or y is None:
        return None
    try:
        if isinstance(x, sympy.Basic) and x.is_number and y.is_number:
            return bool(sympy.simplify(x - y) == 0 or abs(complex(sympy.N(x - y))) < 1e-9)
        return bool(sympy.simplify(x - y) == 0) if not hasattr(x, "is_subset") else x == y
    except Exception:  # noqa: BLE001
        return None


def plain(text: str) -> str:
    return re.sub(r"\\(left|right|text|mathrm|,|;|!)|[\s$]", "", text.lower())


def compare(first: dict, second: dict) -> str:
    if "error" in first or "error" in second:
        return "ERROR"
    if first["needs_figure"] or second["needs_figure"]:
        return "NEEDS_FIGURE"
    if first["kind"] == "PROOF" and second["kind"] == "PROOF":
        return "PROOF"
    values_a = [a["sympy"] for a in first["answers"] if a["sympy"]]
    values_b = [a["sympy"] for a in second["answers"] if a["sympy"]]
    if values_a and values_b and all(any(same(x, y) for y in values_b) for x in values_a) \
            and all(any(same(y, x) for x in values_a) for y in values_b):
        return "AGREE"  # the same set of values, even when the two runs named or grouped the requirements differently
    by_label = {a["label"]: a for a in second["answers"]}
    verdicts = []
    for answer in first["answers"]:
        other = by_label.get(answer["label"])
        if other is None:
            verdicts.append(None)  # labelled differently in the other run: the value check above is the fair test
            continue
        verdict = same(answer["sympy"], other["sympy"])
        if verdict is None and not answer["sympy"] and not other["sympy"]:
            # not a number or an expression (a pair of values, a word): the two texts, without markup, must be the same
            # equal text is agreement; different text proves nothing (a proof worded twice), so it is "not comparable"
            verdict = True if plain(answer["text"]) == plain(other["text"]) else None
        verdicts.append(verdict)
    if not verdicts:
        return "UNPARSED"
    if any(v is False for v in verdicts):
        return "DISAGREE"
    if all(v is True for v in verdicts):
        return "AGREE"
    if any(v is True for v in verdicts):
        return "PARTIAL"  # what can be compared agrees; the rest (a proof, a description) is for the teacher
    return "UNPARSED"


def run(exercise: dict, images: dict[int, bytes]) -> dict:
    first = solve_once(exercise, images, 1.0)
    second = solve_once(exercise, images, 0.6)
    models = [MODEL, MODEL]
    for index, result in enumerate((first, second)):
        if "error" in result:
            replacement = solve_once(exercise, images, 0.8, FALLBACK_MODEL, attempts=3)
            if "error" not in replacement:
                models[index] = FALLBACK_MODEL
                (first, second)[index].clear()
                (first, second)[index].update(replacement)
    return {"id": exercise["id"], "status": compare(first, second), "models": models,
            "unsure": [first.get("unsure"), second.get("unsure")], "runs": [first, second]}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=0, help="solve only this many (spread over the temas)")
    parser.add_argument("--ids", default="")
    parser.add_argument("--workers", type=int, default=3)
    parser.add_argument("--batch", type=int, default=0, help="solve at most this many of the ones not done yet, then stop")
    parser.add_argument("--recompare", action="store_true", help="recompute the statuses of the saved solves, no calls")
    parser.add_argument("--redo-errors", action="store_true", help="drop the exercises that ended in ERROR and solve them again")
    args = parser.parse_args()
    exercises = json.loads((DATA / "exercises.json").read_text(encoding="utf-8"))
    exercises_all = list(exercises)
    out = DATA / "solutions.jsonl"
    saved = [json.loads(line) for line in out.read_text(encoding="utf-8").splitlines()] if out.exists() else []
    if args.recompare or args.redo_errors:
        for row in saved:
            row["status"] = compare(*row["runs"])
        if args.redo_errors:
            saved = [row for row in saved if row["status"] != "ERROR"]
        out.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in saved), encoding="utf-8")
        print("recomparat:", dict(collections.Counter(row["status"] for row in saved)))
        if args.recompare:
            write_progress(exercises_all)
            return
    done = {row["id"] for row in saved}
    if args.ids:
        wanted = set(args.ids.split(","))
        exercises = [e for e in exercises if e["id"] in wanted]
    elif args.limit:
        step = max(1, len(exercises) // args.limit)
        exercises = exercises[::step][: args.limit]
    todo = [e for e in exercises if e["id"] not in done]
    if args.batch:
        todo = todo[: args.batch]  # one step: the next N that are not done yet, in book order
    print(f"{len(todo)} de rezolvat ({len(done)} deja făcute), model {MODEL}")
    images = page_images()
    with concurrent.futures.ThreadPoolExecutor(args.workers) as pool, out.open("a", encoding="utf-8") as handle:
        for result in pool.map(lambda e: run(e, images), todo):
            handle.write(json.dumps(result, ensure_ascii=False) + "\n")
            handle.flush()
            print(result["id"], result["status"])
    write_progress(exercises_all)


def write_progress(exercises: list[dict]) -> None:
    """tutor/data/teme/PROGRES.md: where the pilot stands, so any later session (or a closed laptop) can pick it up."""
    out = DATA / "solutions.jsonl"
    rows = [json.loads(line) for line in out.read_text(encoding="utf-8").splitlines()] if out.exists() else []
    for row in rows:
        row["status"] = compare(*row["runs"])
    done = {row["id"] for row in rows}
    left = [e for e in exercises if e["id"] not in done]
    by_status = collections.Counter(row["status"] for row in rows)
    by_tema = collections.Counter(f"T{e['tema']}" for e in left)
    lines = [
        "# Pilot „Teme recapitulative”: progres",
        "",
        f"Rezolvate: {len(rows)} din {len(exercises)}. Rămase: {len(left)}.",
        "",
        "Statusuri (două rezolvări independente, `gemini-3.1-pro-preview`): "
        + ", ".join(f"{k} {v}" for k, v in sorted(by_status.items())),
        "",
        "Rămase pe teme: " + (", ".join(f"{k} {v}" for k, v in sorted(by_tema.items(), key=lambda kv: int(kv[0][1:]))) or "nimic"),
        "",
        "Următorul pas (un lot de 50, ~15-20 min; se poate repeta oricând, sare ce e gata):",
        "",
        "    cd tutor",
        "    uv run --with pymupdf --with sympy python -X utf8 scripts/pilot_teme_solve.py --batch 50 --workers 4",
        "",
        "Dacă au rămas ERROR (răspuns gol): `--redo-errors`. Raport: `python scripts/pilot_teme_report.py`.",
    ]
    (DATA / "PROGRES.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print((DATA / "PROGRES.md").read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()
