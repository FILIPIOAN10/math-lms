from tutor.evaluate import Question
from tutor.evaluate_help import Verdict, evaluate, is_correct, render, summarize
from tutor.help_chain import HelpResult, Refusal


def verdict(id: str, kind: str, answered: bool, reason: str | None = None, nearest: float | None = 0.3) -> Verdict:
    return Verdict(id, kind, answered, reason, nearest)


def test_a_covered_question_should_be_answered_and_an_uncovered_one_refused():
    assert is_correct(verdict("c01", "concept", answered=True))
    assert is_correct(verdict("e01", "exam", answered=True))
    assert is_correct(verdict("n01", "negative", answered=False, reason="too_far"))
    assert not is_correct(verdict("c02", "concept", answered=False, reason="too_far"))
    assert not is_correct(verdict("n02", "negative", answered=True))


def test_the_summary_counts_each_kind_and_says_where_the_refusals_came_from():
    verdicts = [
        verdict("c01", "concept", True), verdict("c02", "concept", False, "too_far", 0.47),
        verdict("e01", "exam", True, nearest=None),
        verdict("n01", "negative", False, "too_far", 0.54), verdict("n02", "negative", False, "not_covered", 0.40),
        verdict("n03", "negative", True, nearest=0.41),
    ]

    s = summarize(verdicts)

    assert (s["covered"], s["answered"]) == (3, 2)
    assert (s["uncovered"], s["refused"]) == (3, 2)
    assert dict(s["refused_by"]) == {"too_far": 1, "not_covered": 1}
    assert (s["correct"], s["total"]) == (4, 6)
    assert [v.id for v in s["wrong"]] == ["c02", "n03"]


def test_the_report_names_the_wrong_decisions_and_the_threshold_used():
    text = render([verdict("c02", "concept", False, "too_far", 0.4646), verdict("n03", "negative", True, nearest=0.41)],
                  max_distance=0.46, usd=0.0123, model="gpt-6-luna")

    assert "prag de distanță 0.460" in text
    assert "c02: a refuzat (motiv: too_far), cea mai apropiată 0.465" in text
    assert "n03: a răspuns la o întrebare din afara manualului, cea mai apropiată 0.410" in text


class FakeService:
    def run(self, statement, *, slot=None, levels=3) -> HelpResult:
        refused = "cerc" in statement
        return HelpResult(answered=not refused, steps=[], refusal=Refusal(reason="too_far") if refused else None,
                          strategy="vector", best_distance=0.5 if refused else 0.3, passages=[],
                          input_tokens=100, output_tokens=10)


def test_every_question_is_run_at_level_one_with_its_exam_position_and_tokens_are_added_up():
    questions = [Question("c01", "concept", "Ce e o mulțime?", ("AIS8",), None),
                 Question("n01", "negative", "Aria unui cerc?", (), None)]

    verdicts, tokens_in, tokens_out = evaluate(FakeService(), questions)

    assert [(v.id, v.answered, v.reason) for v in verdicts] == [("c01", True, None), ("n01", False, "too_far")]
    assert (tokens_in, tokens_out) == (200, 20)
