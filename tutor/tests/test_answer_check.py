"""Do two solutions give the same final answers? The verdicts the pilot data taught us (tutor/scripts/pilot_teme_*)."""

import pytest

from tutor.answer_check import clean_statement, compare, repair_latex, same


def run(*answers, kind="NUMERIC", needs_figure=False) -> dict:
    return {"kind": kind, "answers": [{"label": l, "text": t, "sympy": s} for l, t, s in answers],
            "needs_figure": needs_figure}


def test_equal_values_agree_even_written_differently():
    assert compare(run(("a", "$2\\sqrt{3}$", "2*sqrt(3)")), run(("a", "√12", "sqrt(12)"))) == "AGREE"


def test_a_different_value_is_a_disagreement():
    assert compare(run(("a", "4", "4"), ("b", "5", "5")), run(("a", "4", "4"), ("b", "6", "6"))) == "DISAGREE"


def test_decimals_and_fractions_are_the_same_set():
    first = run(("", "{-1,2; 0,25}", "FiniteSet(-1.2, 0.25)"))
    second = run(("", "{-6/5; 1/4}", "FiniteSet(Rational(-6,5), Rational(1,4))"))

    assert compare(first, second) == "AGREE"
    assert same("Interval(0.5, 2)", "Interval(Rational(1,2), 2)") is True


def test_labels_written_differently_are_the_same_requirement():
    first = run(("a)", "8", "8"), ("b)", "Nu", None))
    second = run(("a", "8", "8"), ("b", "Nu", "null"))

    assert compare(first, second) == "AGREE"


def test_a_point_with_or_without_its_name_is_the_same_answer():
    assert compare(run(("b", "$(2, 2)$", "Tuple(2, 2)")), run(("b", "$P(2, 2)$", None))) == "AGREE"


def test_one_answer_each_is_compared_whatever_the_labels():
    assert compare(run(("1", "$p=2, q=5, r=7$", None)), run(("", "$p=2, q=5, r=7$", None))) == "AGREE"


@pytest.mark.parametrize("proof_text", ["Demonstrație", "demonstrat", "Se arată că e adevărat"])
def test_two_runs_that_only_say_proof_have_not_been_checked(proof_text):
    first = run(("a", proof_text, None), ("b", "37", "37"))
    second = run(("a", proof_text, None), ("b", "$37$", "37"))

    assert compare(first, second) == "PARTIAL"  # b) agrees; a) is for the teacher


def test_a_proof_part_keeps_an_exercise_out_of_agree_even_when_the_values_match():
    first = run(("a", "Demonstrație", "null"), ("b", "$\\{-8, 6\\}$", "FiniteSet(-8, 6)"))
    second = run(("a", "Demonstrație", None), ("b", "\\{-8, 6\\}", "FiniteSet(-8, 6)"))

    assert compare(first, second) == "PARTIAL"


def test_a_whole_proof_is_proof_and_a_missing_figure_says_so():
    assert compare(run(("", "Demonstrație", None), kind="PROOF"), run(("", "Gata", None), kind="PROOF")) == "PROOF"
    assert compare(run(("", "?", None), needs_figure=True), run(("", "4", "4"))) == "NEEDS_FIGURE"
    assert compare({"error": "empty"}, run(("", "4", "4"))) == "ERROR"


def test_texts_that_differ_and_have_no_value_are_not_comparable():
    assert compare(run(("a", "a treia zi", None)), run(("a", "ziua a treia", None))) == "UNPARSED"


def test_only_safe_sympy_is_evaluated():
    assert same("__import__('os').system('x')", "0") is None
    assert same("exp(1)", "exp(1)") is None  # a name that is not on the list is not evaluated at all


def test_latex_eaten_by_json_escapes_is_put_back():
    broken = {"statement": "$PQ \text{ || } DF$ și $AB \bot CD$, $\frac{1}{2}$", "steps": ["\ta) rămâne"]}

    fixed = repair_latex(broken)

    assert fixed["statement"] == "$PQ \\text{ || } DF$ și $AB \\bot CD$, $\\frac{1}{2}$"
    assert fixed["steps"] == ["\ta) rămâne"]  # a real tab before "a)" is left alone


def test_figure_markers_are_taken_out_of_a_statement():
    statement = "Considerăm funcția $f(x)=2x-1$.\n[figura 2]\na) Calculați $f(1)$.  [figura 3] b) Aflați $x$."

    assert clean_statement(statement) == "Considerăm funcția $f(x)=2x-1$.\na) Calculați $f(1)$. b) Aflați $x$."
