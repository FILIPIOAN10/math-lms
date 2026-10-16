"""Measures how well each retrieval strategy finds the right section of the book for a set of questions.

Run:  uv run python -m tutor.evaluate [--write-report eval/report.md]

The golden set (eval/golden.jsonl) lists, for each question, the section(s) that explain it. A chunk is a hit when
its section is a gold section or lies beneath one. Reported per strategy: hit rate at 1, 3 and 5, and MRR (how high
the first right chunk is). The last table is about refusing: how far the nearest chunk is for questions the book
answers, against questions it does not.
"""

import argparse
import json
import pathlib
import statistics
import sys
from dataclasses import dataclass
from datetime import date

import psycopg

from tutor import page_map
from tutor.config import get_settings
from tutor.llm import embeddings as make_embeddings
from tutor.retrieval import STRATEGIES, Hit, search, slot_baseline

KS = (1, 3, 5)


@dataclass(frozen=True)
class Question:
    id: str
    kind: str  # concept | exam | negative
    question: str
    gold: tuple[str, ...]
    slot: str | None


@dataclass(frozen=True)
class Metrics:
    questions: int
    hit: dict[int, float]
    mrr: float
    returned: float  # chunks handed to the model, on average


def load_golden(path: pathlib.Path) -> list[Question]:
    questions = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            raw = json.loads(line)
            questions.append(Question(raw["id"], raw["kind"], raw["question"], tuple(raw["gold"]), raw.get("slot")))
    return questions


def is_hit(code: str, gold: tuple[str, ...]) -> bool:
    """The chunk is in a gold section or beneath one (AIS5P5 answers a question whose gold is AIS5)."""
    return any(g in page_map.parents(code) for g in gold)


def first_hit_rank(hits: list[Hit], gold: tuple[str, ...]) -> int | None:
    return next((rank for rank, hit in enumerate(hits, start=1) if is_hit(hit.code, gold)), None)


def metrics(ranks: list[int | None], returned: list[int]) -> Metrics:
    n = len(ranks)
    return Metrics(
        questions=n,
        hit={k: sum(1 for r in ranks if r is not None and r <= k) / n for k in KS} if n else {k: 0.0 for k in KS},
        mrr=sum(1 / r for r in ranks if r is not None) / n if n else 0.0,
        returned=statistics.fmean(returned) if returned else 0.0,
    )


def best_threshold(positive: list[float], negative: list[float]) -> tuple[float, float]:
    """The distance below which we answer, and the share of questions it gets right (answer the ones the book covers)."""
    candidates = sorted(set(positive + negative))
    best = (0.0, 0.0)
    for threshold in candidates:
        correct = sum(1 for d in positive if d <= threshold) + sum(1 for d in negative if d > threshold)
        accuracy = correct / (len(positive) + len(negative))
        if accuracy > best[1]:
            best = (threshold, accuracy)
    return best


def evaluate(conn: psycopg.Connection, questions: list[Question], embeddings, k: int = 5) -> dict:
    vectors = embeddings.embed_documents([q.question for q in questions])
    by_id = {q.id: v for q, v in zip(questions, vectors, strict=True)}
    answerable = [q for q in questions if q.kind != "negative"]
    exam = [q for q in questions if q.kind == "exam"]

    def page_of_slot(slot: str | None) -> str | None:
        if slot is None:
            return None
        row = conn.execute(
            "SELECT c.code FROM en_chapter_slots s JOIN en_chapters c ON c.id = s.chapter_id WHERE s.slot = %s", (slot,)
        ).fetchone()
        return row[0] if row else None

    runs: dict[str, dict[str, list[Hit]]] = {}  # strategy -> question id -> hits
    for strategy in STRATEGIES:
        runs[strategy] = {q.id: search(conn, strategy, q.question, embeddings, k=k, query_vector=by_id[q.id])
                          for q in answerable}
    runs["slot baseline"] = {q.id: slot_baseline(conn, q.slot, k) if q.slot else [] for q in exam}
    runs["slot + vector"] = {
        q.id: search(conn, "vector", q.question, embeddings, k=k, only_under=page_of_slot(q.slot), query_vector=by_id[q.id])
        for q in exam
    }
    # The policy we would ship: the exam position when the question has one, meaning search otherwise.
    runs["politica: loc din test, altfel vector"] = {
        q.id: slot_baseline(conn, q.slot, k) if q.slot else runs["vector"][q.id] for q in answerable
    }

    def table(names: list[str], subset: list[Question]) -> dict[str, Metrics]:
        out = {}
        for name in names:
            ranks = [first_hit_rank(runs[name][q.id], q.gold) for q in subset]
            out[name] = metrics(ranks, [len(runs[name][q.id]) for q in subset])
        return out

    distances = {q.id: search(conn, "vector", q.question, embeddings, k=1, query_vector=by_id[q.id]) for q in questions}
    best = {qid: hits[0].score if hits else 1.0 for qid, hits in distances.items()}
    positive = [best[q.id] for q in answerable]
    negative = [best[q.id] for q in questions if q.kind == "negative"]
    misses = {
        name: [q.id for q in (exam if name.startswith("slot") else answerable) if first_hit_rank(runs[name][q.id], q.gold) is None]
        for name in runs
    }
    return {
        "all": table(list(STRATEGIES) + ["politica: loc din test, altfel vector"], answerable),
        "concept": table(list(STRATEGIES), [q for q in answerable if q.kind == "concept"]),
        "exam": table(list(STRATEGIES) + ["slot baseline", "slot + vector"], exam),
        "refusal": {"positive": positive, "negative": negative, "threshold": best_threshold(positive, negative)},
        "misses": misses,
    }


def _row(name: str, m: Metrics) -> str:
    cells = " | ".join(f"{m.hit[k]:.0%}" for k in KS)
    return f"| {name} | {cells} | {m.mrr:.2f} | {m.returned:.1f} |"


def render(results: dict, questions: list[Question], model: str, chunks: int) -> str:
    n_concept = sum(1 for q in questions if q.kind == "concept")
    n_exam = sum(1 for q in questions if q.kind == "exam")
    n_neg = sum(1 for q in questions if q.kind == "negative")
    head = "| strategie | hit@1 | hit@3 | hit@5 | MRR | chunk-uri date |\n|---|---|---|---|---|---|"
    lines = [
        f"# Evaluare retrieval — {date.today().isoformat()}",
        "",
        f"Corpus: {chunks} de chunk-uri (capitolul I). Întrebări: {n_concept} de concept, {n_exam} în stil EN "
        f"(cu locul din test), {n_neg} din afara manualului. Embeddings: {model}.",
        "",
        "Setul de aur e scris de un om după capitol (nu e măsurat pe elevi): cifrele sunt orientative.",
        "",
        "## Toate întrebările la care manualul răspunde",
        head,
        *(_row(name, m) for name, m in results["all"].items()),
        "",
        "## Doar întrebările de concept",
        head,
        *(_row(name, m) for name, m in results["concept"].items()),
        "",
        "## Doar itemurile în stil EN (au un loc în test)",
        head,
        *(_row(name, m) for name, m in results["exam"].items()),
        "",
        "## Refuzul: distanța celui mai apropiat chunk",
    ]
    pos, neg = results["refusal"]["positive"], results["refusal"]["negative"]
    threshold, accuracy = results["refusal"]["threshold"]
    lines += [
        f"- întrebări la care manualul răspunde: {min(pos):.2f} – {max(pos):.2f} (mediana {statistics.median(pos):.2f})",
        f"- întrebări din afara manualului: {min(neg):.2f} – {max(neg):.2f} (mediana {statistics.median(neg):.2f})",
        f"- cel mai bun prag: {threshold:.2f} → {accuracy:.0%} corecte (răspunde sub prag, refuză peste)",
        "",
        "## Unde a ratat (nicio bucată corectă în primele 5)",
    ]
    for name, ids in results["misses"].items():
        lines.append(f"- {name}: {', '.join(ids) if ids else 'nicăieri'}")
    return "\n".join(lines) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Compară strategiile de retrieval pe setul de aur.")
    parser.add_argument("--golden", type=pathlib.Path, default=pathlib.Path("eval/golden.jsonl"))
    parser.add_argument("--k", type=int, default=5)
    parser.add_argument("--write-report", type=pathlib.Path)
    args = parser.parse_args(argv)

    settings = get_settings()
    questions = load_golden(args.golden)
    with psycopg.connect(settings.psycopg_dsn) as conn:
        chunks = conn.execute("SELECT count(*) FROM tutor_chunks").fetchone()[0]
        results = evaluate(conn, questions, make_embeddings(settings), args.k)
    report = render(results, questions, settings.embedding_model, chunks)
    print(report)
    if args.write_report:
        args.write_report.write_text(report, encoding="utf-8")
        print("Scris:", args.write_report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
