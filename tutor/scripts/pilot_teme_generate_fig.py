"""Mini-pilot 2: similar exercises WITH a figure. The model gives the figure as exact coordinates; our code draws it.

For a source exercise that has a figure (and whose two solves AGREED), the strong model sees the book page and the
solved steps and writes 2 new exercises, each with a figure described as points with exact coordinates, segments,
circles, function graphs and textbook marks (teme_figures.FIGURE_SCHEMA). Then, at no token cost, the figure is drawn
(matplotlib) and checked: the lengths and angles written on it must be the ones the coordinates give, a right-angle mark
must be a right angle, the points the statement names should be in it. Last, a SEPARATE call solves the new statement
from scratch, seeing only the statement and the drawn figure, and SymPy compares the final answers (as in the solving
pilot). VERIFIED = the figure checks pass and the two solutions agree; anything else is for the teacher.

    uv run --with pymupdf --with sympy --with matplotlib python -X utf8 scripts/pilot_teme_generate_fig.py [--limit 10] [--workers 2]
    ... scripts/pilot_teme_generate_fig.py --review     (only rebuilds data/teme/figures/review.html, no calls)

Writes tutor/data/teme/generated_fig.jsonl and the drawings in tutor/data/teme/figures/ (resumable: a source already done
is skipped).
"""

import argparse
import base64
import collections
import concurrent.futures
import html
import json
import time

from google.genai import errors, types

import pilot_teme_solve as solver
import teme_figures
from tutor.config import Settings
from tutor.transcribe import gemini_client

DATA = solver.DATA
FIGURES = DATA / "figures"

SYSTEM = """\
You write NEW exercises for Romanian grade-8 students preparing for Evaluarea Națională. You are given one exercise \
from a book page (the image; the exercise is the one quoted below it) with its solved steps. The original has a \
figure. Write exactly 2 new exercises that are similar to it: the same mathematical method and the same difficulty, \
with different numbers or a different but equivalent configuration. Rules:

- Each new exercise has a figure, as in the exam ("În figura alăturată este reprezentat ..."), and you describe that \
figure EXACTLY in `figure`, so that a program can draw it:
  - Coordinates are exact SymPy strings (integers, sqrt(), Rational(a,b), + - * /). Choose them so the figure is TRUE \
to your statement: every length, right angle, midpoint, parallel or equal segment you state must hold exactly in the \
coordinates. Keep all coordinates between -20 and 20.
  - type "plane": z is "0" everywhere. type "solid": z is UP, x goes to the right, y goes INTO the page (depth); put \
the base in the plane z = 0 with its front edge at y = 0, and mark with hidden = true the edges that are behind the \
solid, as a textbook draws them dashed (also the height and diagonals inside the solid). type "graph": a function \
graph in the coordinate plane; give the function(s) in `functions` as SymPy expressions in x, with the x interval to \
draw, and include the origin O and the named points in `points`.
  - Every point named in the statement is in `points`, with the same name ("A", "A'", "A_1").
  - `marks` are what the drawing shows: right angles (points [A, V, B], the vertex V in the middle), lengths written \
on a segment (points [A, B], text as in the statement, e.g. "6 cm"), angles (points [A, V, B], text e.g. "60°"). \
Only write on the figure a length or an angle the statement gives; never write an answer on the figure.
- Keep the structure: the same number of requirements (a, b, ...), asked in the same way.
- Use only the grade-8 curriculum. Choose values so every answer is exact and tidy.
- The final answers must differ from the original's, and the two new exercises must differ from each other.
- Write in Romanian, LaTeX between dollar signs. Give a complete numbered solution and the final answers for each \
requirement, both as "text" and as a "sympy" expression (numbers, + - * / ** sqrt() Abs() pi oo Rational(a,b) \
Interval(a,b) FiniteSet(...) Union(...)), or null when an answer is a proof or a description.
- Do not copy the original's statement. Do not add requirements the original does not have.
"""

SCHEMA = {
    "type": "object",
    "properties": {
        "exercises": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "statement": {"type": "string"},
                    "figure": teme_figures.FIGURE_SCHEMA,
                    "steps": {"type": "array", "items": {"type": "string"}},
                    "kind": {"type": "string", "enum": ["NUMERIC", "EXPRESSION", "SET", "PROOF", "OTHER"]},
                    "answers": solver.SCHEMA["properties"]["answers"],
                    "difficulty": {"type": "string", "enum": ["same", "easier", "harder"]},
                },
                "required": ["statement", "figure", "steps", "kind", "answers", "difficulty"],
            },
        }
    },
    "required": ["exercises"],
}


def generate(source: dict, solved: dict, images: dict[int, bytes], model: str) -> dict:
    run = solved["runs"][0]
    steps = "\n".join(f"{i}. {s}" for i, s in enumerate(run["steps"], start=1))
    answers = "; ".join(f"{a['label']}: {a['text']}" for a in run["answers"])
    parts: list = [types.Part.from_bytes(data=images[p], mime_type="image/jpeg") for p in source["pages"] if p in images]
    parts.append(f"Original exercise:\n{source['statement']}\n\nSolved steps:\n{steps}\n\nFinal answers: {answers}")
    config = types.GenerateContentConfig(
        system_instruction=SYSTEM, response_mime_type="application/json", response_json_schema=SCHEMA,
        max_output_tokens=32_000, temperature=1.0,
        thinking_config=types.ThinkingConfig(thinking_level=types.ThinkingLevel.HIGH))
    client = gemini_client(Settings())
    for attempt in range(3):
        if attempt:
            time.sleep(4 * 3 ** (attempt - 1))
        try:
            response = client.models.generate_content(model=model, contents=parts, config=config)
        except errors.APIError as error:
            return {"error": f"{error.code}"}
        if response.text:
            try:
                data = solver.repair_latex(json.loads(response.text))
            except json.JSONDecodeError:
                continue
            usage = response.usage_metadata
            data["tokens"] = [(usage.prompt_token_count or 0) if usage else 0,
                              (usage.candidates_token_count or 0) if usage else 0,
                              (getattr(usage, "thoughts_token_count", 0) or 0) if usage else 0]
            return data
    return {"error": "empty"}


def verify(statement: str, jpeg: bytes) -> dict:
    """An independent solve of the new statement with only the drawn figure; the other model stands in if it is empty."""
    fake = {"number": 1, "statement": statement, "has_figure": True, "pages": [0]}
    result = solver.solve_once(fake, {0: jpeg}, 0.8)
    if "error" in result:
        result = solver.solve_once(fake, {0: jpeg}, 0.8, solver.FALLBACK_MODEL, attempts=3)
    return result


def process(source: dict, solved: dict, images: dict[int, bytes]) -> dict:
    generated = generate(source, solved, images, solver.MODEL)
    model = solver.MODEL
    if "error" in generated:
        generated, model = generate(source, solved, images, solver.FALLBACK_MODEL), solver.FALLBACK_MODEL
    if "error" in generated:
        return {"source": source["id"], "error": generated["error"]}
    items = []
    for number, item in enumerate(generated["exercises"][:2], start=1):
        name = f"{source['id']}-{number}"
        figure_check = teme_figures.check(item["figure"], item["statement"])
        try:
            png, jpeg = teme_figures.render(item["figure"])
            (FIGURES / f"{name}.png").write_bytes(png)
        except Exception as error:  # noqa: BLE001 - a figure that cannot be drawn is a finding, not a crash
            figure_check["problems"].append(f"nu s-a putut desena: {error}")
            figure_check["ok"], jpeg = False, None
        checked = verify(item["statement"], jpeg) if jpeg else {"error": "no figure"}
        gen_run = {"kind": item["kind"], "answers": item["answers"], "needs_figure": False, "unsure": False}
        status = solver.compare(gen_run, checked) if "error" not in checked else "ERROR"
        items.append({"name": name, "statement": item["statement"], "figure": item["figure"], "steps": item["steps"],
                      "kind": item["kind"], "verifier_kind": checked.get("kind"),
                      "answers": item["answers"], "difficulty": item["difficulty"], "verify": status,
                      "figure_check": figure_check, "verified": status == "AGREE" and figure_check["ok"],
                      "copied": solver.plain(item["statement"]) == solver.plain(source["statement"]),
                      "verifier_answers": checked.get("answers"), "verifier_tokens": checked.get("tokens")})
    return {"source": source["id"], "model": model, "tokens": generated.get("tokens"), "items": items}


def recompare() -> None:
    """Statuses, figure checks and drawings again with the current code; free, no calls."""
    out = DATA / "generated_fig.jsonl"
    rows = [solver.repair_latex(json.loads(line)) for line in out.read_text(encoding="utf-8").splitlines()]
    for row in rows:
        for item in row.get("items", []):
            item["figure_check"] = teme_figures.check(item["figure"], item["statement"])
            try:
                (FIGURES / f"{item['name']}.png").write_bytes(teme_figures.render(item["figure"])[0])
            except Exception as error:  # noqa: BLE001
                item["figure_check"]["problems"].append(f"nu s-a putut desena: {error}")
                item["figure_check"]["ok"] = False
            if item.get("verifier_answers") is not None:
                mine = {"kind": item.get("kind", ""), "answers": item["answers"], "needs_figure": False, "unsure": False}
                theirs = {"kind": item.get("verifier_kind") or "", "answers": item["verifier_answers"],
                          "needs_figure": False, "unsure": False}
                item["verify"] = solver.compare(mine, theirs)
            item["verified"] = item["verify"] == "AGREE" and item["figure_check"]["ok"]
    out.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")
    items = [i for r in rows for i in r.get("items", [])]
    print("recomparat:", dict(collections.Counter(i["verify"] for i in items)),
          "| figuri ok:", sum(i["figure_check"]["ok"] for i in items), "din", len(items),
          "| VERIFIED:", sum(i["verified"] for i in items))


def review() -> None:
    """data/teme/figures/review.html: every generated exercise with its drawing and checks, to look at in a browser."""
    out = DATA / "generated_fig.jsonl"
    rows = [json.loads(line) for line in out.read_text(encoding="utf-8").splitlines()] if out.exists() else []
    cards = []
    for row in rows:
        for item in row.get("items", []):
            path = FIGURES / f"{item['name']}.png"
            img = (f'<img src="data:image/png;base64,{base64.b64encode(path.read_bytes()).decode()}">'
                   if path.exists() else "<p>(fără desen)</p>")
            check = item["figure_check"]
            notes = "".join(f"<li class=bad>{html.escape(p)}</li>" for p in check["problems"]) + \
                "".join(f"<li class=warn>{html.escape(w)}</li>" for w in check["warnings"])
            answers = "; ".join(f"{a['label']}: {a['text']}" for a in item["answers"])
            other = "; ".join(f"{a['label']}: {a['text']}" for a in item.get("verifier_answers") or [])
            badge = "VERIFIED" if item["verified"] else item["verify"] + ("" if check["ok"] else " + figură")
            cards.append(
                f'<section><h2>{item["name"]} <span class="{"ok" if item["verified"] else "no"}">{badge}</span></h2>'
                f'<div class=row><div class=text><p>{html.escape(item["statement"])}</p>'
                f'<p><b>Răspuns generator:</b> {html.escape(answers)}</p><p><b>Răspuns verificator:</b> {html.escape(other)}</p>'
                f'<ul>{notes}</ul></div>{img}</div></section>')
    page = f"""<!doctype html><html lang=ro><head><meta charset=utf-8><title>Figuri generate</title>
<link rel=stylesheet href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
<script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
<script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js"
 onload="renderMathInElement(document.body,{{delimiters:[{{left:'$',right:'$',display:false}}]}})"></script>
<style>body{{font:15px/1.5 system-ui,sans-serif;max-width:1100px;margin:0 auto;padding:16px;background:#fff;color:#111}}
section{{border-top:1px solid #ddd;padding:12px 0}}.row{{display:flex;gap:16px;flex-wrap:wrap}}.text{{flex:1 1 380px}}
img{{max-width:440px;width:100%;border:1px solid #eee}}.ok{{color:#11713a}}.no{{color:#a4161a}}
.bad{{color:#a4161a}}.warn{{color:#8a5a00}}h2{{font-size:17px;margin:0 0 6px}}</style></head><body>
<h1>Exerciții generate cu figură ({sum(len(r.get("items", [])) for r in rows)})</h1>{"".join(cards)}</body></html>"""
    (FIGURES / "review.html").write_text(page, encoding="utf-8")
    print("pagina:", FIGURES / "review.html")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=10)
    parser.add_argument("--workers", type=int, default=2)
    parser.add_argument("--review", action="store_true", help="only rebuild review.html from the saved results")
    parser.add_argument("--recompare", action="store_true", help="statuses, checks and drawings again, no calls")
    args = parser.parse_args()
    FIGURES.mkdir(exist_ok=True)
    if args.review or args.recompare:
        if args.recompare:
            recompare()
        review()
        return
    exercises = json.loads((DATA / "exercises.json").read_text(encoding="utf-8"))
    solved = {json.loads(l)["id"]: json.loads(l) for l in (DATA / "solutions.jsonl").read_text(encoding="utf-8").splitlines()}
    out = DATA / "generated_fig.jsonl"
    done = {json.loads(l)["source"] for l in out.read_text(encoding="utf-8").splitlines()} if out.exists() else set()
    candidates = [e for e in exercises if solved.get(e["id"], {}).get("status") == "AGREE" and e["has_figure"]]
    per_tema: dict[int, list[dict]] = collections.defaultdict(list)
    for e in candidates:
        per_tema[e["tema"]].append(e)
    picked: list[dict] = []  # a spread over the temas: every 3rd of each tema's candidates, round robin
    pools = [per_tema[t][::3] for t in sorted(per_tema)]
    while len(picked) < args.limit and any(pools):
        for pool in pools:
            if pool and len(picked) < args.limit:
                picked.append(pool.pop(0))
    todo = [e for e in picked if e["id"] not in done]
    print(f"{len(todo)} surse cu figură de procesat ({len(done)} deja făcute)", flush=True)
    images = solver.page_images()
    with concurrent.futures.ThreadPoolExecutor(args.workers) as pool, out.open("a", encoding="utf-8") as handle:
        for result in pool.map(lambda e: process(e, solved[e["id"]], images), todo):
            handle.write(json.dumps(result, ensure_ascii=False) + "\n")
            handle.flush()
            print(result["source"], [(i["verify"], i["figure_check"]["ok"]) for i in result.get("items", [])]
                  or result.get("error"), flush=True)
    review()


if __name__ == "__main__":
    main()
