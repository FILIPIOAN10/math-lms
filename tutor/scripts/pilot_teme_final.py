"""Pilot, final report: agreement per tema, what the teacher must check, tokens, and two full solutions as examples."""

import collections
import json
import pathlib

DATA = pathlib.Path(__file__).resolve().parent.parent / "data" / "teme"
exercises = {e["id"]: e for e in json.loads((DATA / "exercises.json").read_text(encoding="utf-8"))}
rows = [json.loads(line) for line in (DATA / "solutions.jsonl").read_text(encoding="utf-8").splitlines()]

print("TOTAL", len(rows), "din", len(exercises), dict(collections.Counter(r["status"] for r in rows)))
print()
by_tema: dict[int, collections.Counter] = collections.defaultdict(collections.Counter)
for r in rows:
    by_tema[exercises[r["id"]]["tema"]][r["status"]] += 1
for tema in sorted(by_tema):
    c = by_tema[tema]
    n = sum(c.values())
    print(f"T{tema:<2} {n:>2} ex  AGREE {c['AGREE']:>2} ({100 * c['AGREE'] // n:>2}%)  PARTIAL {c['PARTIAL']:>2}  PROOF {c['PROOF']:>2}  "
          f"UNPARSED {c['UNPARSED']:>2}  NEEDS_FIGURE {c['NEEDS_FIGURE']}")
print()
tokens = [0, 0, 0]
for r in rows:
    for run in r["runs"]:
        for i, t in enumerate(run.get("tokens", [0, 0, 0])):
            tokens[i] += t
print("tokeni totali: intrare", tokens[0], "| iesire", tokens[1], "| gandire", tokens[2])
print("modele:", dict(collections.Counter(m for r in rows for m in r.get("models", []))))
print("marcate nesigure de model:", [r["id"] for r in rows if any(r["unsure"])])
print()


def show(exercise_id: str) -> None:
    row = next(r for r in rows if r["id"] == exercise_id)
    print("=====", exercise_id, row["status"])
    print(exercises[exercise_id]["statement"][:500])
    run = row["runs"][0]
    for i, step in enumerate(run["steps"][:8], start=1):
        print(f"  {i}. {step[:220]}")
    print("  RASPUNS:", [(a["label"], a["text"][:60]) for a in run["answers"]])
    print()


algebra = next(r["id"] for r in rows if r["status"] == "AGREE" and exercises[r["id"]]["tema"] == 5 and not exercises[r["id"]]["has_figure"])
geometry = next(r["id"] for r in rows if r["status"] == "AGREE" and exercises[r["id"]]["tema"] in (9, 10) and exercises[r["id"]]["has_figure"])
show(algebra)
show(geometry)
