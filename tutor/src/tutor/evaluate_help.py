"""Does the help chain answer what the book covers and refuse what it does not?

Run:  uv run python -X utf8 -m tutor.evaluate_help [--max-distance 0.48] [--write-report eval/help-report.md]

Runs the real chain (real retrieval, real model) at level 1 - the level where the decision to answer is taken - on the
golden set: a question the book covers should get a hint, one it does not should get "not in the book". A refusal can
come from three places, and the report says which: the distance gate, the model saying the passages do not help, or the
citation check. Costs a fraction of a cent per question.
"""

import argparse
import pathlib
import sys
from collections import Counter
from dataclasses import dataclass
from datetime import date

from tutor.config import get_settings
from tutor.evaluate import Question, load_golden
from tutor.help_chain import HelpService
from tutor.transcribe import cost_usd


@dataclass(frozen=True)
class Verdict:
    id: str
    kind: str  # concept | exam | negative
    answered: bool
    reason: str | None  # why it was refused: too_far | no_passages | not_covered | no_citation
    nearest: float | None


def is_correct(verdict: Verdict) -> bool:
    """A covered question should be answered, an uncovered one refused."""
    return verdict.answered == (verdict.kind != "negative")


def summarize(verdicts: list[Verdict]) -> dict:
    covered = [v for v in verdicts if v.kind != "negative"]
    uncovered = [v for v in verdicts if v.kind == "negative"]
    return {
        "covered": len(covered),
        "answered": sum(1 for v in covered if v.answered),
        "uncovered": len(uncovered),
        "refused": sum(1 for v in uncovered if not v.answered),
        "refused_by": Counter(v.reason for v in uncovered if not v.answered),
        "wrong": [v for v in verdicts if not is_correct(v)],
        "correct": sum(1 for v in verdicts if is_correct(v)),
        "total": len(verdicts),
    }


def evaluate(service: HelpService, questions: list[Question]) -> tuple[list[Verdict], int, int]:
    verdicts, tokens_in, tokens_out = [], 0, 0
    for q in questions:
        result = service.run(q.question, slot=q.slot, levels=1)
        tokens_in += result.input_tokens
        tokens_out += result.output_tokens
        verdicts.append(Verdict(q.id, q.kind, result.answered, result.refusal.reason if result.refusal else None,
                                result.best_distance))
    return verdicts, tokens_in, tokens_out


def render(verdicts: list[Verdict], max_distance: float, usd: float, model: str) -> str:
    s = summarize(verdicts)
    lines = [
        f"# Evaluare: răspunde sau refuză — {date.today().isoformat()}",
        "",
        f"Lanțul real (retrieval + {model}), treapta 1, prag de distanță {max_distance:.3f}. "
        f"{s['total']} întrebări din setul de aur; cost ≈ {usd:.4f} USD.",
        "",
        f"- întrebări acoperite de manual, cu indiciu primit: **{s['answered']} din {s['covered']}**",
        f"- întrebări din afara manualului, refuzate: **{s['refused']} din {s['uncovered']}**"
        + (" (" + ", ".join(f"{n} de {reason}" for reason, n in s["refused_by"].items()) + ")" if s["refused_by"] else ""),
        f"- decizii corecte în total: **{s['correct']} din {s['total']}**",
        "",
        "## Unde a greșit",
    ]
    for v in s["wrong"]:
        what = "a răspuns la o întrebare din afara manualului" if v.kind == "negative" else f"a refuzat (motiv: {v.reason})"
        near = f", cea mai apropiată {v.nearest:.3f}" if v.nearest is not None else ""
        lines.append(f"- {v.id}: {what}{near}")
    if not s["wrong"]:
        lines.append("- nicăieri")
    lines += ["", "Setul e mic și scris de un singur om: cifrele sunt orientative."]
    return "\n".join(lines) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Măsoară dacă lanțul răspunde când trebuie și refuză când trebuie.")
    parser.add_argument("--golden", type=pathlib.Path, default=pathlib.Path("eval/golden.jsonl"))
    parser.add_argument("--max-distance", type=float, help="pragul de distanță (implicit: din setări)")
    parser.add_argument("--write-report", type=pathlib.Path)
    args = parser.parse_args(argv)

    sys.stdout.reconfigure(encoding="utf-8")
    settings = get_settings()
    if args.max_distance is not None:
        settings = settings.model_copy(update={"max_distance": args.max_distance})
    verdicts, tokens_in, tokens_out = evaluate(HelpService.from_settings(settings), load_golden(args.golden))
    report = render(verdicts, settings.max_distance, cost_usd(tokens_in, tokens_out, settings), settings.chat_model)
    print(report)
    if args.write_report:
        args.write_report.write_text(report, encoding="utf-8")
        print("Scris:", args.write_report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
