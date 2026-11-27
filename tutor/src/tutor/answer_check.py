"""Do two solutions of one exercise give the same final answers? Pure functions, no model and no network.

Each solution lists its final answers per requirement (a, b, ...) both as text and as a SymPy expression. Two answers
agree when SymPy says their difference is zero (or the sets are equal); answers that are not numbers (a pair of values,
a word) agree only when their texts are the same; a proof cannot be checked this way and is left to the teacher.

    compare(first, second) -> AGREE | PARTIAL | PROOF | NEEDS_FIGURE | UNPARSED | DISAGREE | ERROR

Measured on the 387 exercises of the "Teme recapitulative" pilot (tutor/scripts/pilot_teme_*.py).
"""

import re

import sympy

STATUSES = ("AGREE", "PARTIAL", "PROOF", "NEEDS_FIGURE", "UNPARSED", "DISAGREE", "ERROR")

_SAFE = re.compile(r"^[0-9A-Za-z_+\-*/^().,\s]+$")
_ALLOWED = ("sqrt", "Abs", "pi", "oo", "Rational", "Interval", "FiniteSet", "Union")
PROOF_WORDS = re.compile(r"demonstr|arăt|arat|verific|adevărat|adevarat|q\.?e\.?d|proof")

# A model that writes LaTeX inside JSON sometimes forgets to double the backslash, and then JSON reads "\text" as a TAB
# and "ext", "\bot" as a backspace and "ot", "\frac" as a form feed and "rac". Followed by letters, such a control
# character can only have been a LaTeX command (a real TAB before "a)" is followed by one letter and a bracket).
_BROKEN_LATEX = [(re.compile(r"\x08(?=[A-Za-z])"), r"\\b"), (re.compile(r"\x0c(?=[A-Za-z])"), r"\\f"),
                 (re.compile(r"\t(?=[A-Za-z]{2})"), r"\\t"), (re.compile(r"\r(?=[A-Za-z]{2})"), r"\\r")]
_FIGURE_PLACEHOLDER = re.compile(r"[ \t]*\[\s*figura\s*\d*\s*\][ \t]*", re.IGNORECASE)


def repair_latex(value):
    """The same JSON value with the LaTeX commands that JSON escapes ate put back (strings, lists and dicts)."""
    if isinstance(value, str):
        for pattern, replacement in _BROKEN_LATEX:
            value = pattern.sub(replacement, value)
        return value
    if isinstance(value, list):
        return [repair_latex(v) for v in value]
    if isinstance(value, dict):
        return {k: repair_latex(v) for k, v in value.items()}
    return value


# In a transcribed page the same slip also turns "\neq" into a new line and "eq" (a page's text has real new lines,
# so only the LaTeX words that cannot start a Romanian line are put back), and the transcription once wrote "\belong"
# for the sign of belonging.
_BROKEN_NEWLINE = re.compile(r"\n(?=(eq|otin|abla|eg|ewline|ot)\b)")


def repair_page_latex(text: str) -> str:
    """A transcribed page with the LaTeX that JSON escapes ate put back."""
    text = repair_latex(text)
    text = _BROKEN_NEWLINE.sub(lambda _: "\\n", text)
    return text.replace("\\belong", "\\in")


_NOTE = re.compile(r"<!--.*?-->", re.DOTALL)
# "figura 3", "Figura alăturată", "figurii", "(fig. 3)", "[figura 1]" - but not "figurează" (another word).
_FIGURE_WORD = re.compile(r"\bfigur(a|ă|ii|ile|i)\b|\bfig\.\s*\d", re.IGNORECASE)
_FIGURE_NUMBER = re.compile(r"(?<!\[)\b(?:figura|figurii|fig\.)\s*(\d+)", re.IGNORECASE)


def mentions_figure(text: str) -> bool:
    """Whether a statement points at a printed drawing ("În figura 1", "figura alăturată", "(fig. 3)"). The notes the
    transcription leaves in HTML comments ("No figures found") do not count."""
    return bool(_FIGURE_WORD.search(_NOTE.sub(" ", text or "")))


def figure_numbers(text: str) -> list[int]:
    """The book's figure numbers a statement cites, in order ("figura 17" -> 17). A page marker "[figura 1]" is the
    transcription's own count on the page, not the book's number, so it is left out."""
    out: list[int] = []
    for match in _FIGURE_NUMBER.finditer(_NOTE.sub(" ", text or "")):
        if int(match.group(1)) not in out:
            out.append(int(match.group(1)))
    return out


def clean_statement(text: str) -> str:
    """A statement without the "[figura N]" markers the page transcription leaves where a drawing was, and without its
    notes in HTML comments."""
    out: list[str] = []
    for line in _NOTE.sub("", text).strip().splitlines():
        if _FIGURE_PLACEHOLDER.search(line) and not _FIGURE_PLACEHOLDER.sub("", line).strip():
            continue  # a line that was only a marker
        line = _FIGURE_PLACEHOLDER.sub(" ", line).rstrip()
        if line.strip() or (out and out[-1].strip()):  # at most one empty line in a row
            out.append(line if line.strip() else "")
    return "\n".join(out).strip()


def to_expr(text: str | None):
    """The SymPy value of an answer, or None when it is not one. Only numbers, operators and the listed names."""
    if not text or not _SAFE.match(text) or "__" in text:
        return None
    if any(name not in _ALLOWED for name in re.findall(r"[A-Za-z_]\w*", text)):
        return None  # only the listed names: the string is evaluated, so nothing else may be named
    names = {n: getattr(sympy, n) for n in _ALLOWED}
    scope: dict = {}
    exec("from sympy import Integer, Float, Rational, Symbol", scope)  # what the number transformations need
    scope.update(names)
    try:
        return sympy.parse_expr(text.replace("^", "**"), local_dict=names, global_dict=scope)
    except Exception:  # noqa: BLE001 - any parse problem means "not comparable"
        return None


def exact(value):
    """Decimals as fractions (-1.2 -> -6/5), so {-1.2; 0.25} and {-6/5; 1/4} are the same set."""
    floats = value.atoms(sympy.Float) if isinstance(value, sympy.Basic) else set()
    return value.xreplace({f: sympy.Rational(str(f)) for f in floats}) if floats else value


def same(a: str | None, b: str | None) -> bool | None:
    """True/False when both answers can be compared as math, None when one of them is not an expression."""
    x, y = to_expr(a), to_expr(b)
    if x is None or y is None:
        return None
    x, y = exact(x), exact(y)
    try:
        if isinstance(x, sympy.Basic) and x.is_number and y.is_number:
            return bool(sympy.simplify(x - y) == 0 or abs(complex(sympy.N(x - y))) < 1e-9)
        return bool(sympy.simplify(x - y) == 0) if not hasattr(x, "is_subset") else x == y
    except Exception:  # noqa: BLE001
        return None


def plain(text: str) -> str:
    return re.sub(r"\\(left|right|text|mathrm|,|;|!)|[\s$]", "", text.lower())


def label_key(label: str) -> str:
    """"a)", "a.", "$a$" are one requirement."""
    return re.sub(r"[^0-9a-z]", "", plain(label))


def answer_key(text: str) -> str:
    """A point written with or without its name, "P(2, 2)" or "(2, 2)", is the same answer."""
    return re.sub(r"^[a-z]'*(?=\()", "", plain(text))


def _value(answer: dict) -> str | None:
    return None if answer.get("sympy") in (None, "", "null", "None") else answer["sympy"]


def compare(first: dict, second: dict) -> str:
    """The verdict on two solutions, each {"kind", "answers": [{"label", "text", "sympy"}], "needs_figure"} or
    {"error": ...}."""
    if "error" in first or "error" in second:
        return "ERROR"
    if first.get("needs_figure") or second.get("needs_figure"):
        return "NEEDS_FIGURE"
    if first.get("kind") == "PROOF" and second.get("kind") == "PROOF":
        return "PROOF"
    values_a = [_value(a) for a in first["answers"]]
    values_b = [_value(a) for a in second["answers"]]
    # The same set of values, even when the two runs named or grouped the requirements differently. Only when every
    # answer has a value: a requirement without one (a proof) has not been checked, so it cannot be part of an AGREE.
    if values_a and values_b and all(values_a) and all(values_b) \
            and all(any(same(x, y) for y in values_b) for x in values_a) \
            and all(any(same(y, x) for x in values_a) for y in values_b):
        return "AGREE"
    by_label = {label_key(a["label"]): a for a in second["answers"]}  # "$a$", "a)": one label
    verdicts = []
    for answer in first["answers"]:
        other = by_label.get(label_key(answer["label"]))
        if other is None and len(first["answers"]) == 1 and len(second["answers"]) == 1:
            other = second["answers"][0]  # one answer each: they answer the same question, whatever the label
        if other is None:
            verdicts.append(None)  # labelled differently in the other run: the value check above is the fair test
            continue
        verdict = same(_value(answer), _value(other))
        if verdict is None:
            # Not comparable as math (a pair of values, a word, a SymPy form we do not read): the two texts, without
            # markup, must be the same. Equal text is agreement; different text proves nothing, so "not comparable".
            # Two runs that both just say "Demonstrație" have not been checked against each other at all.
            mine, theirs = answer_key(answer["text"]), answer_key(other["text"])
            verdict = True if mine == theirs and not PROOF_WORDS.search(mine) else None
        verdicts.append(verdict)
    if not verdicts:
        return "UNPARSED"
    if any(v is False for v in verdicts):
        return "DISAGREE"
    if all(v is True for v in verdicts):
        return "AGREE"
    if any(v is True for v in verdicts):
        return "PARTIAL"  # what can be compared agrees; the rest (a proof, a description) is for the teacher
    return "UNPARSED"
