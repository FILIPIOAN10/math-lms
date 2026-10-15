"""Which chapter of the textbook a printed page belongs to, from its table of contents (Cuprins).

Codes are the ones of the LMS chapter pages: letters and digits only. A chapter is `AI`, a section `AIS5`, a
subsection `AIS5P5` (Algebră, chapter I, section 5, point 5); geometry uses `G`.
"""

import re

# First page of geometry; everything before it is algebra (Cuprins: "GEOMETRIE ... Capitolul I ... 113").
GEOMETRY_STARTS = 113

# (first printed page, last printed page, chapter code). Gaps (for example pp. 51-71, the tests) are unmapped.
CHAPTER_PAGES: list[tuple[int, int, str]] = [
    (9, 21, "AI"), (22, 24, "AII"), (25, 34, "AIII"), (35, 41, "AIV"), (42, 44, "AV"), (45, 50, "AVI"),
    (72, 75, "AVII"), (76, 87, "AVIII"), (88, 93, "AIX"), (94, 112, "AX"),
    (113, 114, "GI"), (115, 123, "GII"), (124, 148, "GIII"), (149, 154, "GIV"), (155, 163, "GV"),
    (164, 173, "GVI"), (174, 220, "GVII"),
]

ROMAN = {"I": 1, "II": 2, "III": 3, "IV": 4, "V": 5, "VI": 6, "VII": 7, "VIII": 8, "IX": 9, "X": 10}
_CHAPTER_HEADING = re.compile(r"^\s*([IVX]+)\.\s*(?=[^\d\s])")


def area_of(page: int) -> str:
    return "G" if page >= GEOMETRY_STARTS else "A"


def chapter_for_page(page: int) -> str | None:
    for first, last, code in CHAPTER_PAGES:
        if first <= page <= last:
            return code
    return None


def chapter_from_heading(title: str, page: int) -> str | None:
    """'I. MULȚIMEA NUMERELOR NATURALE' on a geometry page is GI, on an algebra page AI."""
    match = _CHAPTER_HEADING.match(title)
    if match is None or match.group(1) not in ROMAN:
        return None
    return area_of(page) + match.group(1)


def section_code(chapter: str, section: int | None, point: int | None) -> str:
    code = chapter
    if section is not None:
        code += f"S{section}"
        if point is not None:
            code += f"P{point}"
    return code


def parents(code: str) -> list[str]:
    """AIS5P5 -> [AIS5P5, AIS5, AI]: the most specific code first, for falling back to a page that exists."""
    out = [code]
    while True:
        shorter = re.sub(r"(S\d+|P\d+)$", "", out[-1])
        if shorter == out[-1]:
            return out
        out.append(shorter)
