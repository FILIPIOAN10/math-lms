"""Do two solutions give the same final answers? The verdicts the pilot data taught us (tutor/scripts/pilot_teme_*)."""

import pytest

from tutor.answer_check import (clean_statement, compare, figure_numbers, mentions_figure, repair_latex,
                                 repair_page_latex, same, to_expr)


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


def test_a_power_tower_is_refused_before_anything_computes_it():
    """Health audit 2026-11-19 M1: SymPy computed 9^9^9 and never finished. Refused at once now; ordinary powers stay."""
    import time

    started = time.monotonic()
    assert to_expr("9^9^9") is None
    assert same("9^9^9", "1") is None
    assert to_expr("2^1000") is None  # an exponent past the limit
    assert time.monotonic() - started < 3
    assert same("2^10", "1024") is True
    assert same("(1/2)^2", "0.25") is True
    assert same("3*sqrt(2)", "sqrt(18)") is True


def test_latex_eaten_by_json_escapes_is_put_back():
    broken = {"statement": "$PQ \text{ || } DF$ și $AB \bot CD$, $\frac{1}{2}$", "steps": ["\ta) rămâne"]}

    fixed = repair_latex(broken)

    assert fixed["statement"] == "$PQ \\text{ || } DF$ și $AB \\bot CD$, $\\frac{1}{2}$"
    assert fixed["steps"] == ["\ta) rămâne"]  # a real tab before "a)" is left alone


def test_a_transcribed_page_gets_its_latex_back():
    page = "***Atenție!*** $(a, b)\neq (b, a)$ și $a \x08elong A$, $2 \times 3$.\nUn rând nou rămâne."

    assert repair_page_latex(page) == ("***Atenție!*** $(a, b)\\neq (b, a)$ și $a \\in A$, $2 \\times 3$.\n"
                                       "Un rând nou rămâne.")


def test_a_new_page_transcription_comes_back_repaired():
    from tutor.transcribe import transcription

    page = transcription({"page_number": 3, "markdown": "$x\neq 0$", "figures": [], "warnings": []}, "m", 1, 1)

    assert page.markdown == "$x\\neq 0$"


def test_figure_markers_are_taken_out_of_a_statement():
    statement = "Considerăm funcția $f(x)=2x-1$.\n[figura 2]\na) Calculați $f(1)$.  [figura 3] b) Aflați $x$."

    assert clean_statement(statement) == "Considerăm funcția $f(x)=2x-1$.\na) Calculați $f(1)$. b) Aflați $x$."


@pytest.mark.parametrize("statement", [
    "În figura 1, punctele $A$, $B$ și $C$ sunt coliniare.",
    "Graficul funcțiilor este prezentat în figura alăturată.",
    "Figura 2 reprezintă o hartă.",
    "Fie dreptunghiul $ABCD$ din figura 17, cu $AD=5$.",
    "Triunghiul $ABC$ (fig. 3) are $AB=6$.",
    "[figura 1]\na) Calculați aria.",
    "Aria sectorului din figurii alăturate.",
])
def test_a_statement_that_points_at_a_drawing_mentions_a_figure(statement):
    assert mentions_figure(statement)


@pytest.mark.parametrize("statement", [
    "Calculați $\frac{48}{6}$.\n<!-- warning: No figures found. -->",
    "Un număr figurează pe listă de două ori.",
    "Desenați un pătrat cu latura de 3 cm.",
])
def test_a_transcription_note_or_another_word_is_not_a_figure(statement):
    assert not mentions_figure(statement)


def test_the_figure_numbers_a_statement_points_at_are_read_in_order():
    assert figure_numbers("În figura 17 este reprezentat un paralelogram (figura 18).") == [17, 18]
    assert figure_numbers("În figura 10, $ABCDA'B'C'D'$ este o prismă.") == [10]
    assert figure_numbers("[figura 1] Figura alăturată") == []  # page markers and "alăturată" carry no book number


def test_clean_statement_drops_the_transcription_notes():
    assert clean_statement("Calculați $1+1$.\n<!-- warning: No figures found. -->") == "Calculați $1+1$."
