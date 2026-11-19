"""Mini-pilot: generates similar exercises from solved ones and checks each with an independent solve.

For every source exercise (only ones whose two solves AGREED, and that need no figure) the strong model writes 2 new
exercises - same method and difficulty, other values or context - each with a full solution. A SEPARATE call then solves
each new statement from scratch, without seeing that solution, and the final answers are compared the same way as in the
solving pilot (SymPy). VERIFIED = the two agree; anything else is for the teacher.

    uv run --with pymupdf --with sympy python -X utf8 scripts/pilot_teme_generate.py [--limit 30] [--workers 2]

Writes tutor/data/teme/generated.jsonl (resumable: a source already done is skipped).
"""

import argparse
import collections
import concurrent.futures
import json
import pathlib
import time

from google.genai import errors, types

import pilot_teme_solve as solver
from tutor.config import Settings
from tutor.transcribe import gemini_client

DATA = solver.DATA

SYSTEM = """\
You write NEW exercises for Romanian grade-8 students preparing for Evaluarea Națională. You are given one exercise \
with its solved steps. Write exactly 2 new exercises that are similar to it: the same mathematical method and the \
same difficulty, with different numbers, expressions or a different context. Rules:

- Each new exercise is complete text with no figure and no reference to a figure ("în figura ..."). If the original \
depends on a figure, describe the situation in words instead.
- Keep the structure: the same number of requirements (a, b, ...), asked in the same way.
- Use only the grade-8 curriculum. Choose values so every answer is exact and tidy (no ugly roots or fractions unless \
the original has them).
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
                    "steps": {"type": "array", "items": {"type": "string"}},
                    "kind": {"type": "string", "enum": ["NUMERIC", "EXPRESSION", "SET", "PROOF", "OTHER"]},
                    "answers": solver.SCHEMA["properties"]["answers"],
                    "difficulty": {"type": "string", "enum": ["same", "easier", "harder"]},
                },
                "required": ["statement", "steps", "kind", "answers", "difficulty"],
            },
        }
    },
    "required": ["exercises"],
}


def generate(source: dict, solved: dict, model: str) -> dict:
    run = solved["runs"][0]
    steps = "\n".join(f"{i}. {s}" for i, s in enumerate(run["steps"], start=1))
    answers = "; ".join(f"{a['label']}: {a['text']}" for a in run["answers"])
    prompt = f"Original exercise:\n{source['statement']}\n\nSolved steps:\n{steps}\n\nFinal answers: {answers}"
    config = types.GenerateContentConfig(
        system_instruction=SYSTEM, response_mime_type="application/json", response_json_schema=SCHEMA,
        max_output_tokens=24_000, temperature=1.0,
        thinking_config=types.ThinkingConfig(thinking_level=types.ThinkingLevel.HIGH))
    client = gemini_client(Settings())
    for attempt in range(3):
        if attempt:
            time.sleep(4 * 3 ** (attempt - 1))
        try:
            response = client.models.generate_content(model=model, contents=[prompt], config=config)
        except errors.APIError as error:
            return {"error": f"{error.code}"}
        if response.text:
            try:
                return solver.repair_latex(json.loads(response.text))
            except json.JSONDecodeError:
                continue
    return {"error": "empty"}


def verify(statement: str) -> dict:
    """An independent solve of the new statement, from scratch; the strong model, the other one standing in if it is empty."""
    fake = {"number": 1, "statement": statement, "has_figure": False, "pages": []}
    result = solver.solve_once(fake, {}, 0.8)
    if "error" in result:
        result = solver.solve_once(fake, {}, 0.8, solver.FALLBACK_MODEL, attempts=3)
    return result


def process(source: dict, solved: dict) -> dict:
    generated = generate(source, solved, solver.MODEL)
    if "error" in generated:
        generated = generate(source, solved, solver.FALLBACK_MODEL)
    if "error" in generated:
        return {"source": source["id"], "error": generated["error"]}
    items = []
    for item in generated["exercises"][:2]:
        checked = verify(item["statement"])
        gen_run = {"kind": item["kind"], "answers": item["answers"], "needs_figure": False, "unsure": False}
        status = solver.compare(gen_run, checked) if "error" not in checked else "ERROR"
        same_as_original = solver.plain(item["statement"]) == solver.plain(source["statement"])
        items.append({"statement": item["statement"], "steps": item["steps"], "kind": item["kind"],
                      "answers": item["answers"], "difficulty": item["difficulty"], "verify": status,
                      "copied": same_as_original, "verifier_kind": checked.get("kind"),
                      "verifier_answers": checked.get("answers")})
    return {"source": source["id"], "items": items}


def recompare(out: pathlib.Path) -> None:
    """The statuses again with the current comparator (it learns new answer forms); free, no calls."""
    rows = [solver.repair_latex(json.loads(line)) for line in out.read_text(encoding="utf-8").splitlines()]
    for row in rows:
        for item in row.get("items", []):
            if item.get("verifier_answers") is None:
                continue
            mine = {"kind": item.get("kind", ""), "answers": item["answers"], "needs_figure": False, "unsure": False}
            theirs = {"kind": item.get("verifier_kind") or "", "answers": item["verifier_answers"], "needs_figure": False,
                      "unsure": False}
            item["verify"] = solver.compare(mine, theirs)
    out.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")
    print("recomparat:", dict(collections.Counter(i["verify"] for r in rows for i in r.get("items", []))))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=30)
    parser.add_argument("--workers", type=int, default=2)
    parser.add_argument("--recompare", action="store_true", help="recompute the statuses of the saved results, no calls")
    args = parser.parse_args()
    out = DATA / "generated.jsonl"
    if args.recompare:
        recompare(out)
        return
    exercises = json.loads((DATA / "exercises.json").read_text(encoding="utf-8"))
    solved = {json.loads(l)["id"]: json.loads(l) for l in (DATA / "solutions.jsonl").read_text(encoding="utf-8").splitlines()}
    done = {json.loads(l)["source"] for l in out.read_text(encoding="utf-8").splitlines()} if out.exists() else set()
    candidates = [e for e in exercises if solved.get(e["id"], {}).get("status") == "AGREE" and not e["has_figure"]]
    per_tema: dict[int, list[dict]] = collections.defaultdict(list)
    for e in candidates:
        per_tema[e["tema"]].append(e)
    # a spread over the temas: every 4th of each tema's candidates, round robin, until the limit
    picked: list[dict] = []
    pools = [per_tema[t][::4] for t in sorted(per_tema)]
    while len(picked) < args.limit and any(pools):
        for pool in pools:
            if pool and len(picked) < args.limit:
                picked.append(pool.pop(0))
    todo = [e for e in picked if e["id"] not in done]
    print(f"{len(todo)} surse de procesat ({len(done)} deja făcute)")
    with concurrent.futures.ThreadPoolExecutor(args.workers) as pool, out.open("a", encoding="utf-8") as handle:
        for result in pool.map(lambda e: process(e, solved[e["id"]]), todo):
            handle.write(json.dumps(result, ensure_ascii=False) + "\n")
            handle.flush()
            print(result["source"], [i["verify"] for i in result.get("items", [])] or result.get("error"))


if __name__ == "__main__":
    main()
