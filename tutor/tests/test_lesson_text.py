"""A lesson page's transcript turned into what the app's reader shows: figures in place, LaTeX and Markdown it knows."""

from tutor.lesson_text import Placed, app_markdown


def test_each_figure_becomes_the_drawing_the_text_or_a_note_about_the_book_page():
    transcript = ("---\nbook_page: 12\n---\n\nTriunghiul:\n[figura 1]\nTabelul:\n[figura 2]\nȘi o poză:\n[figura 3]\n"
                  "Fără nimic:\n[figura 4]")
    figures = {1: Placed("Triunghiul ABC [dreptunghic]", drawing_id=41),
               2: Placed("Tabel", text="| a | b |\n|---|---|\n| 1 | 2 |"),
               3: Placed("Un cub din lemn")}

    text = app_markdown(transcript, figures)

    assert text == ("Triunghiul:\n\n![Triunghiul ABC  dreptunghic](desen:41)\n\nTabelul:\n\n| a | b |\n|---|---|\n| 1 | 2 |"
                    "\n\nȘi o poză:\n\n*(Un cub din lemn: o vezi pe pagina din carte.)*\n\nFără nimic:\n\n"
                    "*(O figură: o vezi pe pagina din carte.)*")


def test_the_markdown_and_html_the_reader_does_not_know_are_turned_into_what_it_does():
    text = app_markdown("<u>**Proprietăți**</u>\n1) _Laturile opuse sunt paralele_ ($AB||CD$, $x_1 + x_2$).\n"
                        "***Atenție!*** $a_n$", {})

    assert text == "**Proprietăți**\n1) Laturile opuse sunt paralele ($AB||CD$, $x_1 + x_2$).\n**Atenție!** $a_n$"


def test_a_system_written_over_several_lines_becomes_a_display_formula():
    text = app_markdown("În final, $\\left.\\begin{aligned} & A = 54 \\\\ & DM = 6 \\end{aligned}\\right\\} "
                        "\\Rightarrow 54$\nRăspuns.", {})

    assert text.startswith("În final, $\\left.") and "$$" not in text  # on one line it stays inline
    multi = app_markdown("Deci $\\begin{aligned}\nx &= 1 \\\\\ny &= 2\n\\end{aligned}$ gata.", {})
    assert "\n\n$$\\begin{aligned}\nx &= 1 \\\\\ny &= 2\n\\end{aligned}$$\n\n" in multi


def test_the_latex_slips_of_the_transcription_are_repaired():
    text = app_markdown("($\n\n\\sphericalangle A\\equiv B$) și $(5\\\\sqrt{3})^2$ și $\\begin{array}{r@{\\,}l} a \\end{array}$"
                        " și $a\x08elong A$", {})

    assert text == "($\\sphericalangle A\\equiv B$) și $(5\\sqrt{3})^2$ și $\\begin{array}{rl} a \\end{array}$ și $a\\in A$"
