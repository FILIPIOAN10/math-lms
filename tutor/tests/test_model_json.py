"""The model writes LaTeX inside its JSON and often leaves the backslashes single: a command JSON does not know
("\\circ") breaks the whole answer, one it does know ("\\times" = tab + "imes") silently eats a letter. Both are repaired
before the text reaches a teacher or a student."""

from types import SimpleNamespace

import pytest

from tutor.help_chain import Draft, HelpError, _drafted


def raw(text: str) -> SimpleNamespace:
    # Gemini answers in parts; the structured-output parser left `parsed` empty because the JSON did not load.
    return SimpleNamespace(content=[{"type": "text", "text": text}], usage_metadata={"input_tokens": 5, "output_tokens": 7})


def test_an_unknown_latex_escape_is_repaired_instead_of_failing_the_answer():
    answer = r'{"covered": true, "text": "Unghiul are $70^\circ$, nu $120^\circ$.", "source_ids": [1]}'

    drafted = _drafted({"parsed": None, "raw": raw(answer)})

    assert drafted.draft.text == r"Unghiul are $70^\circ$, nu $120^\circ$."
    assert drafted.draft.source_ids == [1] and drafted.output_tokens == 7


def test_already_doubled_backslashes_and_real_newlines_stay_as_they_are():
    answer = r'{"covered": true, "text": "$\\frac{1}{2}$ și $\neq$\nRândul doi", "source_ids": [2]}'

    drafted = _drafted({"parsed": None, "raw": raw(answer)})

    assert drafted.draft.text == "$\\frac{1}{2}$ și $\\neq$\nRândul doi"


def test_a_latex_command_eaten_as_a_json_escape_gets_its_backslash_back():
    eaten = Draft(covered=True, text="$2 \times 8$ și $\frac{1}{2}$", source_ids=[1])  # a tab and a form feed

    drafted = _drafted({"parsed": eaten, "raw": raw("")})

    assert drafted.draft.text == r"$2 \times 8$ și $\frac{1}{2}$"


def test_an_answer_that_is_not_json_at_all_is_still_an_error():
    with pytest.raises(HelpError):
        _drafted({"parsed": None, "raw": raw("Nu pot răspunde.")})
