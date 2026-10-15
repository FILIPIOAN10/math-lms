import pathlib

import pytest

from tutor import page_map
from tutor.chunking import (
    Page,
    chunk_pages,
    content_hash,
    figure_token,
    is_example,
    link_figures,
    parse_transcript,
    read_transcript,
)

PAGE_9 = Page("book.pdf", 9, """# I. MULȚIMEA NUMERELOR NATURALE

## 1. Introducere

Textul introductiv despre mulțimea $\\mathbb{N}$.

### 1.1. AXA NUMERELOR

O dreaptă cu un punct de origine.

## 2. Adunarea și scăderea numerelor naturale

### 2.1. ADUNAREA

Proprietățile adunării: $(a+b)+c=a+(b+c)$.""")

PAGE_10 = Page("book.pdf", 10, """Continuarea adunării: $1+2+3+...+n=\\frac{n\\cdot(n+1)}{2}$.

## 2.2. SCĂDEREA

Dacă $a-b=d$, atunci $a$ este descăzutul.

# 3. Înmulțirea numerelor naturale

## 3.1. ÎNMULȚIREA

Dacă $a\\cdot b=c$, atunci $c$ este produsul.""")


def codes(documents):
    return [d.metadata["code"] for d in documents]


def test_pages_are_cut_at_numbered_headings_into_lms_codes():
    documents, problems = chunk_pages([PAGE_9, PAGE_10])

    assert problems == []
    assert codes(documents) == ["AIS1", "AIS1P1", "AIS2P1", "AIS2P1", "AIS2P2", "AIS3P1"]
    # the chapter title travels with the first section instead of being a chunk of its own
    assert documents[0].page_content.startswith("# I. MULȚIMEA NUMERELOR NATURALE")


def test_text_before_the_first_heading_continues_the_previous_page_section():
    documents, _ = chunk_pages([PAGE_9, PAGE_10])

    continuation = documents[3]
    assert continuation.metadata["code"] == "AIS2P1"
    assert continuation.metadata["book_page"] == 10
    assert "Continuarea adunării" in continuation.page_content


def test_headings_without_a_space_after_the_dot_are_still_sections():
    # as the photos really come out: "1.1.AXA NUMERELOR", "2.Adunarea", "8.Mulțimi"
    page = Page("book.pdf", 9, "# I. MULȚIMEA NUMERELOR NATURALE\n\n### 1.1.AXA NUMERELOR\n\nDreapta.\n\n"
                               "## 2.Adunarea și scăderea\n\n### 2.1.ADUNAREA\n\nSuma.\n\n# 8.Mulțimi\n\nCe e o mulțime.")

    documents, _ = chunk_pages([page])

    assert codes(documents) == ["AIS1P1", "AIS2P1", "AIS8"]


def test_a_numbered_list_item_is_not_taken_for_a_section():
    page = Page("book.pdf", 18, "### 7.3. CRITERII DE DIVIZIBILITATE\n\nIntro.\n\n## 7) Criteriul de divizibilitate cu 10\n\nSe termină în 0.")

    documents, _ = chunk_pages([page])

    assert codes(documents) == ["AIS7P3"]
    assert "Criteriul de divizibilitate cu 10" in documents[0].page_content


def test_markdown_inside_a_formulas_text_is_removed_so_the_formula_typesets():
    raw = "---\nbook_page: 14\n---\n\n$=39\\quad\\text{**_Răspuns corect: b)._**}$ și **bold** $x_1$"

    page = parse_transcript(raw, "AI")

    assert page.markdown == "$=39\\quad\\text{Răspuns corect: b).}$ și **bold** $x_1$"


def test_a_figure_placeholder_becomes_the_part_of_the_photo_that_shows_it():
    raw = ("---\nbook_page: 9\nfigures: "
           '[{"number": 1, "description": "Axa numerelor, cu punctele O, A, M, L", '
           '"x": 0.10, "y": 0.50, "width": 0.60, "height": 0.08}]\n---\n\n'
           "În figura de mai jos, lui $0$ îi corespunde $O$.\n\n[figura 1]\n\nUrmează textul.")

    page = link_figures(parse_transcript(raw, "AI"), photo_id=42, padding=0.02)

    assert "![Axa numerelor, cu punctele O, A, M, L](poza:42?x=0.080&y=0.480&w=0.640&h=0.120)" in page.markdown
    assert "[figura 1]" not in page.markdown


def test_a_figure_the_model_did_not_locate_is_left_as_plain_text():
    page = link_figures(Page("AI", 9, "Text\n\n[figura 3]"), photo_id=1)

    assert page.markdown == "Text\n\n[figură]"


def test_a_figure_box_never_leaves_the_photo():
    token = figure_token(7, {"x": 0.0, "y": 0.95, "width": 1.0, "height": 0.05, "description": "jos"}, padding=0.05)

    assert token == "![jos](poza:7?x=0.000&y=0.900&w=1.000&h=0.100)"


def test_a_figure_description_cannot_break_the_link():
    token = figure_token(1, {"x": 0.1, "y": 0.1, "width": 0.2, "height": 0.2, "description": "Axa (O) [A]\nM"})

    assert token.startswith("![Axa  O   A  M](poza:1?")


def test_a_gap_in_page_numbers_does_not_inherit_the_section():
    distant = Page("book.pdf", 13, "Text care continuă ceva din altă parte.")

    documents, _ = chunk_pages([PAGE_9, distant])

    assert documents[-1].metadata["code"] == "AI"  # chapter by page number, no section guessed


def test_a_page_outside_the_cuprins_is_reported_and_skipped():
    tests_page = Page("book.pdf", 58, "Testul 1. Subiectul I.")

    documents, problems = chunk_pages([tests_page])

    assert documents == []
    assert any("p.58" in problem for problem in problems)


def test_a_page_without_a_printed_number_is_reported():
    documents, problems = chunk_pages([Page("book.pdf", None, "text")])

    assert documents == []
    assert any("fără număr" in problem for problem in problems)


def test_geometry_pages_get_geometry_codes():
    page = Page("book.pdf", 134, "# III. TRIUNGHIUL\n\n## 7. Triunghiul dreptunghic\n\n### 7.1. Teoreme\n\nText.")

    documents, _ = chunk_pages([page])

    assert codes(documents) == ["GIIIS7P1"]


def test_a_long_paragraph_is_split_but_a_table_stays_whole():
    long_paragraph = " ".join(["propoziție de umplutură cu $x+1$;"] * 120)
    table = "| a | b |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |"
    page = Page("book.pdf", 9, f"## 1. Introducere\n\n{long_paragraph}\n\n{table}")

    documents, _ = chunk_pages([page], max_chars=500, min_chars=50)

    assert len(documents) > 2
    assert all(len(d.page_content) <= 500 + 80 for d in documents if "| a | b |" not in d.page_content)
    assert sum("| a | b |" in d.page_content for d in documents) == 1
    assert sum("| 3 | 4 |" in d.page_content for d in documents) == 1


def test_worked_examples_are_told_apart_from_theory():
    assert is_example("**Ex:** $1+2+3=6$")
    assert is_example("### Exerciții\n\nCalculează $2+2$.")
    assert not is_example("**Reținem!** termen = sumă - celălalt termen")


def test_hash_ignores_surrounding_whitespace():
    assert content_hash("  a+b \n") == content_hash("a+b")
    assert len(content_hash("a")) == 64


@pytest.mark.parametrize("page,expected", [(9, "AI"), (21, "AI"), (22, "AII"), (58, None), (113, "GI"), (188, "GVII")])
def test_chapter_for_page_follows_the_cuprins(page, expected):
    assert page_map.chapter_for_page(page) == expected


def test_parents_go_from_specific_to_general():
    assert page_map.parents("AIS5P5") == ["AIS5P5", "AIS5", "AI"]
    assert page_map.parents("GIIIS10") == ["GIIIS10", "GIII"]
    assert page_map.parents("AI") == ["AI"]


def test_a_roman_numeral_heading_picks_the_area_from_the_page():
    assert page_map.chapter_from_heading("I. MULȚIMEA NUMERELOR NATURALE", 9) == "AI"
    assert page_map.chapter_from_heading("I. NOȚIUNI INTRODUCTIVE", 113) == "GI"
    assert page_map.chapter_from_heading("3. Înmulțirea", 10) is None


def test_read_transcript_parses_the_front_matter(tmp_path: pathlib.Path):
    path = tmp_path / "p01.md"
    path.write_text("---\nsource_pdf: I. MULTIMEA.pdf\npdf_page: 1\nbook_page: 9\n---\n\n# Titlu\n", encoding="utf-8")

    page = read_transcript(path)

    assert (page.source_name, page.book_page, page.markdown) == ("I. MULTIMEA.pdf", 9, "# Titlu")
