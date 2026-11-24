"""Plan steps 3-5 for many chapters, one after another: write the lesson, check it, correct it (at most `--rounds`
times) while something is red or yellow, then store it in the app as a draft for the teacher. One chapter at a time:
parallel runs made Gemini answer 504. A chapter that fails is reported and the next one goes on.

    uv run python -X utf8 scripts/build_lessons.py --chapters A1,A2 [--rounds 2] [--no-store]

Writes the log to data/lessons/build.log and a summary to data/lessons/summary.json.
"""

import argparse
import json
import pathlib
import subprocess
import sys
import time

DATA = pathlib.Path(__file__).resolve().parent.parent / "data" / "lessons"


def step(log, *args: str) -> bool:
    started = time.monotonic()
    result = subprocess.run([sys.executable, "-X", "utf8", "-m", *args], capture_output=True, text=True,
                            encoding="utf-8", errors="replace")
    log.write(f"$ {' '.join(args)}  ({time.monotonic() - started:.0f} s, exit {result.returncode})\n")
    log.write(result.stdout + (result.stderr[-2000:] if result.returncode else "") + "\n")
    log.flush()
    return result.returncode == 0


def counts(code: str) -> dict:
    return json.loads((DATA / code / "review.json").read_text(encoding="utf-8"))["counts"]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--chapters", required=True)
    parser.add_argument("--rounds", type=int, default=2, help="corrections at most, while red or yellow remain")
    parser.add_argument("--no-store", action="store_true", help="do not put the drafts in the app")
    args = parser.parse_args()
    DATA.mkdir(parents=True, exist_ok=True)
    summary_path = DATA / "summary.json"
    summary = json.loads(summary_path.read_text(encoding="utf-8")) if summary_path.exists() else {}
    with (DATA / "build.log").open("a", encoding="utf-8") as log:
        for code in [c.strip().upper() for c in args.chapters.split(",") if c.strip()]:
            log.write(f"\n===== {code} {time.strftime('%H:%M:%S')}\n")
            entry = {"ok": False, "revisions": 0}
            if step(log, "tutor.synthesize", "--chapter", code) and step(log, "tutor.verify_lesson", "--chapter", code):
                for _ in range(args.rounds):
                    now = counts(code)
                    if not now["red"] and not now["yellow"]:
                        break
                    if not (step(log, "tutor.synthesize", "--chapter", code, "--revise")
                            and step(log, "tutor.verify_lesson", "--chapter", code)):
                        break
                    entry["revisions"] += 1
                entry["counts"] = counts(code)
                entry["ok"] = True
                if not args.no_store:
                    entry["stored"] = step(log, "tutor.store_lesson", "--chapter", code)
            summary[code] = entry
            summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=1), encoding="utf-8")
            print(code, entry, flush=True)


if __name__ == "__main__":
    main()
