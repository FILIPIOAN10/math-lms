"""A lesson photo's transcript as the app shows it (V73, en_photo_lessons): the LaTeX repaired, the bits of Markdown and
HTML the app's reader (frontend MarkdownText) does not know turned into ones it does, and every [figura N] replaced by
the app's drawing of it, by the figure written as text (a table, a calculation), or by a note sending the reader to
the book page. Pure text in, text out: tutor/scripts/publish_lessons.py writes the result, clean_lessons.py shows it.
"""

import re
from dataclasses import dataclass

from tutor.answer_check import repair_page_latex

MARKER = re.compile(r"\[\s*figura\s+(\d+)\s*\]", re.IGNORECASE)
_HEADER = re.compile(r"^---\n.*?\n---\n", re.S)
_AFTER_DOLLAR = re.compile(r"\$[ \t]*\n\s*(?=\\)")  # "($" + empty lines + "\sphericalangle A...$": one formula
_ARRAY_SPEC = re.compile(r"(\\begin\{array\}\{)([^{}]*(?:\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}[^{}]*)*)\}")
_ARRAY_SPACING = re.compile(r"@\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}")  # {r@{\,}l}: right LaTeX, but KaTeX knows no @{...}
# A system written inline over several lines ($\left.\begin{aligned} ... \end{aligned}\right\} ...$) becomes a display.
_SYSTEM = re.compile(r"\$((?:\\left(?:\\\{|[.({\[|])?\s*)?\\begin\{(\w+\*?)\}[\s\S]+?\\end\{\2\}[^$]*)\$")
_MATH = re.compile(r"\$\$[\s\S]+?\$\$|\$[^$\n]+?\$")
_TAGS = re.compile(r"</?(?:u|b|i|em|strong|span|sup|sub|br)\b[^>]*>", re.IGNORECASE)
_UNDERSCORE_ITALIC = re.compile(r"(?<![\w\\])_([^_\n]+?)_(?!\w)")


def readable(text: str) -> str:
    """The LaTeX as KaTeX can draw it: JSON-escape slips put back, "$" and its formula on one piece, "\\sqrt" escaped
    twice made "\sqrt" again, and the @{...} spacing of array columns (KaTeX has none) dropped."""
    text = _AFTER_DOLLAR.sub("$", repair_page_latex(text))
    text = re.sub(r"\\\\(?=[A-Za-z])", lambda _: "\\", text)
    return _ARRAY_SPEC.sub(lambda m: m.group(1) + _ARRAY_SPACING.sub("", m.group(2)) + "}", text)


def _outside_math(text: str, change) -> str:
    """`change` applied to the text between formulas only (a formula's _ and * are LaTeX)."""
    out, last = [], 0
    for match in _MATH.finditer(text):
        out.append(change(text[last:match.start()]))
        out.append(match.group(0))
        last = match.end()
    out.append(change(text[last:]))
    return "".join(out)


@dataclass(frozen=True)
class Placed:
    """What stands where a figure was: the app's drawing, the figure as text, or neither (the book page has it)."""

    description: str = ""
    drawing_id: int | None = None
    text: str | None = None


def _alt(description: str) -> str:
    return re.sub(r"[\[\]\n]", " ", description).strip() or "Figură"


def app_markdown(transcript: str, figures: dict[int, Placed]) -> str:
    text = readable(_HEADER.sub("", transcript, count=1).strip())
    text = _SYSTEM.sub(lambda m: f"\n\n$${m.group(1)}$$\n\n" if "\n" in m.group(1) else m.group(0), text)
    text = _TAGS.sub("", text)
    text = re.sub(r"\*\*\*(.+?)\*\*\*", r"**\1**", text)
    text = _outside_math(text, lambda part: _UNDERSCORE_ITALIC.sub(r"\1", part))

    def place(match: re.Match) -> str:
        figure = figures.get(int(match.group(1)))
        if figure is not None and figure.drawing_id is not None:
            return f"\n\n![{_alt(figure.description)}](desen:{figure.drawing_id})\n\n"
        if figure is not None and figure.text:
            return f"\n\n{readable(figure.text).strip()}\n\n"
        what = figure.description.strip() if figure is not None and figure.description.strip() else "O figură"
        return f"\n\n*({what}: o vezi pe pagina din carte.)*\n\n"

    text = MARKER.sub(place, text)
    return re.sub(r"\n{3,}", "\n\n", text).strip()
