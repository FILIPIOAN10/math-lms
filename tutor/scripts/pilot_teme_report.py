"""Pilot, step 4: what the solving run produced - agreement per status, tokens, and the exercises a teacher must check.

    python scripts/pilot_teme_report.py [--first 40]
"""

import argparse
import collections
import json
import pathlib

DATA = pathlib.Path(__file__).resolve().parent.parent / "data" / "teme"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--first", type=int, default=0, help="only the first N lines of solutions.jsonl")
    args = parser.parse_args()
    lines = (DATA / "solutions.jsonl").read_text(encoding="utf-8").splitlines()
    rows = [json.loads(line) for line in lines][: args.first or None]
    exercises = {e["id"]: e for e in json.loads((DATA / "exercises.json").read_text(encoding="utf-8"))}
    status = collections.Counter(r["status"] for r in rows)
    print(len(rows), "exerciții:", dict(status))
    tokens = [0, 0, 0]
    unsure = 0
    for r in rows:
        for run in r["runs"]:
            for i, t in enumerate(run.get("tokens", [0, 0, 0])):
                tokens[i] += t
        unsure += any(r["unsure"])
    n = max(1, len(rows))
    print(f"tokeni medii pe exercițiu (2 rezolvări): in {tokens[0] // n}, out {tokens[1] // n}, gândire {tokens[2] // n}")
    print("marcate nesigure de model:", unsure)
    figure = [r for r in rows if exercises[r["id"]]["has_figure"]]
    print("cu figură:", len(figure), dict(collections.Counter(r["status"] for r in figure)))
    for wanted in ("DISAGREE", "ERROR", "NEEDS_FIGURE", "UNPARSED"):
        ids = [r["id"] for r in rows if r["status"] == wanted]
        if ids:
            print(wanted, ":", ", ".join(ids))


if __name__ == "__main__":
    main()
