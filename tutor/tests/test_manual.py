"""A whole textbook (grades 5-8) into the tutor's store: its printed pages mapped lesson by lesson to the EN chapters,
transcribed page by page (resumable, with a cost cap), cut into passages under the chapter of their lesson."""

import json
import pathlib

import pytest

from tutor.chunking import Page
from tutor.manual import ManualSpec, documents_for, load_spec, markdown_page, pdf_page_of, theory_only

SPEC = {
    "name": "Manual clasa a V-a (Intuitext)",
    "pdf": "manual.pdf",
    "pdf_offset": 2,
    "lessons": [[12, 13, "A5", "Numere naturale"], [24, 25, "A1", "Operații"], [96, 96, "A12", "Fracții"]],
}


def spec(tmp_path: pathlib.Path) -> ManualSpec:
    path = tmp_path / "cls5.json"
    path.write_text(json.dumps(SPEC), encoding="utf-8")
    return load_spec(path)


def test_only_the_pages_of_the_mapped_lessons_are_read(tmp_path):
    manual = spec(tmp_path)

    assert manual.pages() == [12, 13, 24, 25, 96]
    assert manual.chapter_of(25) == "A1" and manual.chapter_of(20) is None  # an Evaluare page is left out
    assert pdf_page_of(manual, 104) == 106  # the cover and the title page come before printed page 1


def test_overlapping_lessons_are_refused(tmp_path):
    path = tmp_path / "bad.json"
    path.write_text(json.dumps({**SPEC, "lessons": [[12, 20, "A5", "x"], [20, 25, "A1", "y"]]}), encoding="utf-8")

    with pytest.raises(ValueError, match="20"):
        load_spec(path)


def test_the_printed_page_comes_from_the_map_not_from_the_model():
    text = markdown_page("Manual clasa a V-a (Intuitext)", 106, 104, "## Important\nc.m.m.d.c. ...", "m", 0.001)

    assert "book_page: 104" in text and "pdf_page: 106" in text
    assert text.rstrip().endswith("c.m.m.d.c. ...")


def test_each_page_goes_under_the_chapter_of_its_lesson_named_by_the_manual(tmp_path):
    manual = spec(tmp_path)
    pages = [
        Page(manual.name, 12, "## 1. Scrierea numerelor naturale\n" + "Un număr natural se scrie cu cifre. " * 12),
        Page(manual.name, 24, "## Adunarea\n" + "Adunarea este comutativă: $a+b=b+a$. " * 12),
        Page(manual.name, 96, "## Fracții ordinare\n" + "O fracție are numărător și numitor. " * 12),
    ]

    documents = documents_for(manual, pages)

    codes = {d.metadata["book_page"]: d.metadata["code"] for d in documents}
    assert codes[12] in ("A5", "A5S1") and codes[24] == "A1" and codes[96] == "A12"
    assert all(d.metadata["source_name"] == "Manual clasa a V-a (Intuitext)" for d in documents)


def test_a_lesson_heading_with_a_roman_numeral_does_not_move_the_page_to_another_chapter(tmp_path):
    # "II. Proprietăți" on a page of the fractions lesson is not chapter AII of some other book
    manual = spec(tmp_path)

    documents = documents_for(manual, [Page(manual.name, 96, "## II. Proprietăți\n" + "Text despre fracții. " * 20)])

    assert {d.metadata["code"] for d in documents} == {"A12"}


def test_only_the_theory_of_a_page_is_kept_its_exercises_are_not_searched():
    page = "\n".join([
        "## Unități de măsură pentru lungime. Perimetre",
        "**Amintește-ți!**",
        "1 Completează spațiile punctate.",
        "**Important**",
        "* Suma lungimilor laturilor unui poligon este **perimetrul poligonului (P)**.",
        "**Exersează!**",
        "2 Transformă în metri: a) 7 dam;",
        "**Problemă rezolvată**",
        r"Un pătrat are latura 5 cm. $P = 4 \cdot 5 = 20$ cm.",
        "**Observă și descoperă!**",
        "3 Măsoară laturile.",
    ])

    kept = theory_only(page)

    assert "perimetrul poligonului" in kept and "P = 4" in kept and "Perimetre" in kept
    assert "Completează" not in kept and "7 dam" not in kept and "Măsoară" not in kept
