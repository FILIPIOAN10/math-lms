"""Plan step 4: every claim of a lesson draft checked before the teacher reads it; what was not checked is never
green, and a red claim flags the draft."""

import time

from tutor.config import Settings
from tutor.solver import Asked
from tutor.verify_lesson import (
    Checker,
    bare_latex,
    combine,
    equal,
    mechanical,
    report_html,
    review_request,
    verify,
)


def claim(text: str, *sources: str, checks=(), conflict: str = "") -> dict:
    return {"text": text, "source_ids": list(sources), "own": not sources, "conflict": conflict,
            "checks": [{"lhs": lhs, "rhs": rhs} for lhs, rhs in checks]}


def draft(**lesson) -> dict:
    base = {"prerequisites": [], "idea": [], "definitions": [], "formulas": [], "problem_types": [], "mistakes": [],
            "recap": []}
    return {"chapter": {"id": 1, "code": "A11", "title": "Funcția liniară"}, "model": "m", "cost_usd": 0.1,
            "chapters": [["A10", "Calcul algebric"]],
            "sources": [{"id": "EN-1", "kind": "EN", "label": "Culegere EN, pagina 108", "text": "$f(x)=ax+b$"}],
            "lesson": {**base, **lesson}}


def always(result: str):
    return lambda lhs, rhs: result


def test_sympy_says_whether_a_computation_holds():
    assert equal("2*2 - 1", "3") == "ok"
    assert equal("(x - 1)**2", "x**2 - 2*x + 1") == "ok"
    assert equal("sqrt(8)", "2*sqrt(2)") == "ok"
    assert equal("Rational(1, 2) + Rational(1, 3)", "Rational(5, 6)") == "ok"
    assert equal("2*2 - 1", "4") == "wrong"
    assert equal("(x + 1)**2", "x**2 + 1") == "wrong"


def test_what_is_not_plain_mathematics_is_not_evaluated():
    assert equal("__import__('os')", "1") == "unparsable"
    assert equal("open(x)", "1") == "unparsable"
    assert equal("9**9**9", "1") == "unparsable"  # it would never finish (health audit 2026-11-19, M1)
    assert equal("2**1000", "1") == "unparsable"
    assert equal("f(2)", "3") == "unparsable"  # a function name is not a one-letter variable


def test_a_computation_that_runs_too_long_is_cut_and_the_next_one_still_runs():
    with Checker(seconds=60) as check:
        assert check("2 + 2", "4") == "ok"  # the worker has started
        check.seconds = 2
        started = time.monotonic()
        assert check("(a + b + c + x + y)**60", "0") == "timeout"  # expanding it takes far longer than 2 s
        assert time.monotonic() - started < 10
        check.seconds = 60
        assert check("2 + 3", "5") == "ok"  # a fresh worker took over


def test_sources_and_computations_colour_each_claim():
    lesson = draft(
        idea=[claim("Graficul e o dreaptă.", "EN-1")],
        formulas=[claim("$f(0)=b$", "EN-9"), claim("Leg ideile.")],
        recap=[claim("$2+2=5$", "EN-1", checks=[("2+2", "5")]), claim("ori", "EN-1", conflict="EN scrie altfel")],
    )

    found = mechanical(lesson, lambda lhs, rhs: equal(lhs, rhs))

    assert found["idea.1"]["colour"] == "green"
    assert found["formula.1"]["colour"] == "red" and "sursă inexistentă: EN-9" in found["formula.1"]["reasons"]
    assert found["formula.2"]["colour"] == "yellow"
    assert found["recap.1"]["colour"] == "red" and found["recap.1"]["checks"][0]["result"] == "wrong"
    assert found["recap.2"]["colour"] == "yellow"


def test_a_check_that_could_not_run_is_never_green():
    lesson = draft(idea=[claim("$f(2)=3$", "EN-1", checks=[("f(2)", "3")])])

    assert mechanical(lesson, always("unparsable"))["idea.1"]["colour"] == "yellow"


def test_a_prerequisite_must_be_a_real_chapter():
    lesson = draft(prerequisites=[{"code": "A10", "idea": "x", "why": "y"}, {"code": "Z9", "idea": "x", "why": "y"}])

    found = mechanical(lesson, always("ok"))

    assert "pre.1" not in found and found["pre.2"]["colour"] == "red"


def test_the_reviewer_sees_each_claim_with_only_the_sources_it_cites():
    lesson = draft(idea=[claim("Graficul e o dreaptă.", "EN-1")])
    lesson["sources"].append({"id": "MAN-2", "kind": "MANUAL", "label": "Manual", "text": "necitat"})

    asked = review_request(lesson)

    assert '<source id="EN-1"' in asked.text and "MAN-2" not in asked.text
    assert '"id": "idea.1"' in asked.text and "You did not write it" in asked.system


def test_the_reviewers_verdict_can_only_make_a_claim_worse():
    found = {"idea.1": {"colour": "green", "reasons": [], "checks": []},
             "idea.2": {"colour": "green", "reasons": [], "checks": []},
             "idea.3": {"colour": "red", "reasons": ["calcul greșit"], "checks": []},
             "idea.4": {"colour": "green", "reasons": [], "checks": [{"lhs": "1", "rhs": "1", "result": "ok"}]},
             "idea.5": {"colour": "green", "reasons": [], "checks": []}}
    reviews = [{"claim_id": "idea.1", "verdict": "supported", "check_matches_text": None, "reason": ""},
               {"claim_id": "idea.2", "verdict": "missing_condition", "check_matches_text": None, "reason": "a ≠ 0"},
               {"claim_id": "idea.3", "verdict": "supported", "check_matches_text": None, "reason": ""},
               {"claim_id": "idea.4", "verdict": "supported", "check_matches_text": False, "reason": ""}]

    result = combine(found, reviews)

    assert [result[f"idea.{i}"]["colour"] for i in range(1, 6)] == ["green", "red", "red", "red", "yellow"]
    assert "lipsește o condiție: a ≠ 0" in result["idea.2"]["reasons"]
    assert "recenzentul n-a citit-o" in result["idea.5"]["reasons"]
    assert {e["colour"] for e in combine(found, None).values()} == {"yellow", "red"}  # no review: nothing green


def test_a_red_claim_flags_the_draft_and_the_report_shows_it():
    lesson = draft(idea=[claim("Graficul e o dreaptă.", "EN-1")], recap=[claim("$f(0)=a$", "EN-1")])

    def ask(req, model, attempts):
        return Asked({"reviews": [
            {"claim_id": "idea.1", "verdict": "supported", "check_matches_text": None, "reason": ""},
            {"claim_id": "recap.1", "verdict": "contradicted", "check_matches_text": None, "reason": "e $b$"}]},
            model, 10, 20)

    review = verify(lesson, always("ok"), ask, Settings())

    assert review["status"] == "flagged" and review["counts"] == {"green": 1, "yellow": 0, "red": 1}
    page = report_html(lesson, review)
    assert 'class="claim red" id="recap.1"' in page and "contrazis de sursă: e $b$" in page
    assert '<a href="#recap.1">recap.1</a>' in page and "EN-1 · Culegere EN, pagina 108" in page


def test_the_free_checks_run_again_on_the_last_reviewers_verdicts_without_a_new_call():
    lesson = draft(idea=[claim("Graficul e o dreaptă.", "EN-1")], recap=[claim("$f(0)=a$", "EN-1")])
    old = {"review_model": "m", "input_tokens": 10, "output_tokens": 20, "claims": {
        "idea.1": {"colour": "green", "reasons": [], "verdict": "supported"},
        "recap.1": {"colour": "red", "reasons": ["contrazis de sursă: e $b$"], "verdict": "contradicted"}}}

    def ask(req, model, attempts):
        raise AssertionError("no model call when reusing")

    review = verify(lesson, always("ok"), ask, Settings(), reuse=old)

    assert review["counts"] == {"green": 1, "yellow": 0, "red": 1}
    assert review["reviews"][1] == {"claim_id": "recap.1", "verdict": "contradicted", "check_matches_text": None,
                                    "reason": "e $b$"}
    assert verify(lesson, always("ok"), ask, Settings(), reuse=review)["counts"] == review["counts"]


def test_a_formula_outside_dollar_signs_is_red_because_the_student_would_see_code():
    assert bare_latex(r"AB = \sqrt{(x_B - x_A)^2}") and bare_latex("Fie $f(x) = 2x") and bare_latex("x^2 + 1")
    assert not bare_latex(r"Pentru $a \neq 0$ punctul este $$\left(-\frac{b}{a}, 0\right)$$.")
    assert not bare_latex("Graficul este o dreaptă.")
    found = mechanical(draft(idea=[claim(r"AB = \sqrt{40}", "EN-1")]), always("ok"))
    assert found["idea.1"]["colour"] == "red" and "formulă fără $…$" in found["idea.1"]["reasons"][0]


def test_a_language_slip_makes_the_claim_yellow():
    found = {"idea.1": {"colour": "green", "reasons": [], "checks": []}}
    reviews = [{"claim_id": "idea.1", "verdict": "supported", "check_matches_text": None, "reason": "",
                "language": "„Dedudecem” → „Deducem”"}]

    result = combine(found, reviews)["idea.1"]

    assert result["colour"] == "yellow" and result["reasons"] == ["limba: „Dedudecem” → „Deducem”"]
