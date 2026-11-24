"""Plan step 3: a new lesson per chapter, written from all the chapter's sources, every claim citing them."""

from tutor.config import Settings
from tutor.solver import Asked
from tutor.synthesize import (
    ask_strong,
    Source,
    chapter_sources,
    claims,
    lesson_markdown,
    repair_claims,
    request,
    revise_request,
    synthesize,
)

CHAPTER = {"id": 1, "code": "A11", "title": "Funcția liniară"}
SOURCES = [Source("EN-1", "EN", "Culegere EN, pagina 108", "$f(x)=ax+b$. Graficul este o dreaptă."),
           Source("MAN-2", "MANUAL", "Manual clasa a VIII-a, pagina 40", "Funcția liniară ..."),
           Source("EX-3", "EXAMPLE", "Model rezolvat", "Enunț: f(2)? Rezolvare: 2*2-1=3")]


def claim(text: str, *sources: str, checks=()) -> dict:
    return {"text": text, "source_ids": list(sources), "own": not sources, "conflict": "", "checks": list(checks)}


LESSON = {
    "prerequisites": [{"code": "A10", "idea": "Calcul algebric", "why": "înlocuiești $x$"}],
    "idea": [claim("Graficul este o dreaptă.", "EN-1")],
    "definitions": [{"term": "Funcția liniară", "claims": [claim("$f(x)=ax+b$", "EN-1")],
                     "example": claim("$f(x)=2x-1$", "EX-3"), "counterexample": claim("")}],
    "formulas": [claim("$f(0)=b$", "EN-1")],
    "problem_types": [{"name": "Calculul unei valori", "method": [claim("Înlocuiești $x$.", "EN-1")],
                       "examples": [{"statement": claim("Calculează $f(2)$ pentru $f(x)=2x-1$.", "EX-3"),
                                     "steps": [claim("$f(2)=2\\cdot 2-1=3$", "EX-3", checks=[{"lhs": "2*2-1", "rhs": "3"}])],
                                     "answer": claim("$3$", "EX-3")}]}],
    "mistakes": [claim("Atenție la semn.", "MAN-2")],
    "recap": [claim("$f(x)=ax+b$", "EN-1")],
}


def test_the_prompt_gives_every_source_with_its_id_and_the_chapters_to_point_at():
    asked = request(CHAPTER, SOURCES, [("A10", "Calcul algebric"), ("A11", "Funcția liniară")])

    assert '<source id="EN-1" kind="EN" where="Culegere EN, pagina 108">' in asked.text
    assert '<source id="EX-3"' in asked.text and "A10: Calcul algebric" in asked.text
    assert "A11: Funcția liniară" not in asked.text.split("<chapters>")[1].split("</chapters>")[0]
    assert "Do not invent" in asked.system and "conflict" in asked.system


def test_every_claim_has_one_id_in_reading_order_and_an_empty_counterexample_is_none():
    ids = [claim_id for claim_id, _, _ in claims(LESSON)]

    assert ids == ["idea.1", "def1.1", "def1.ex", "formula.1", "type1.m1", "type1.ex1.s", "type1.ex1.p1",
                   "type1.ex1.r", "mistake.1", "recap.1"]


def test_the_lesson_reads_in_the_fixed_template():
    text = lesson_markdown(LESSON, CHAPTER)

    headings = [line for line in text.splitlines() if line.startswith("## ")]
    assert headings == ["## Ce trebuie să știi înainte", "## Ideea pe scurt", "## Definiții",
                        "## Formule și proprietăți", "## Tipuri de probleme EN", "## Greșeli frecvente",
                        "## Fișă de recapitulare"]
    assert "**Exemplul 1.** Calculează $f(2)$" in text and "Contraexemplu" not in text
    assert "⟦type1.ex1.p1⟧$f(2)=2\\cdot 2-1=3$" in lesson_markdown(LESSON, CHAPTER, anchors=True)


def test_the_draft_keeps_the_sources_the_chapters_and_the_cost():
    seen = []

    def ask(req, model, attempts):
        seen.append(model)
        return Asked(LESSON, model, 1000, 2000)

    draft = synthesize(ask, Settings(), CHAPTER, SOURCES, [("A10", "Calcul algebric")])

    assert seen == [Settings().solve_model]
    assert draft["lesson"] == LESSON and draft["chapters"] == [["A10", "Calcul algebric"]]
    assert [s["id"] for s in draft["sources"]] == ["EN-1", "MAN-2", "EX-3"]
    assert draft["cost_usd"] > 0


def test_the_sources_are_the_chapter_its_sections_and_the_teachers_summary(conn):
    conn.execute("UPDATE en_chapters SET parent_id = (SELECT id FROM en_chapters WHERE code = 'AI'), "
                 "notes = 'nu e al capitolului' WHERE code = 'AIS2'")
    conn.execute("UPDATE en_chapters SET notes = 'rezumat' WHERE code = 'AI'")
    vector = "[" + ",".join(["0"] * 1536) + "]"
    for code, source, name, page, content in (("AIS2", "PHOTO", "AI (poze LMS)", 12, "din culegere"),
                                              ("AI", "MANUAL", "Manual clasa a V-a", 30, "din manual")):
        conn.execute(
            "INSERT INTO tutor_chunks (chapter_id, kind, position, content, content_hash, embedded_with, embedding, "
            "source, source_name, book_page) SELECT id, 'THEORY', 1, %s, md5(%s) || md5(%s), 'm', %s::vector, %s, %s, %s "
            "FROM en_chapters WHERE code = %s", (content, content, code, vector, source, name, page, code))

    chapter, sources = chapter_sources(conn, "AI")

    assert chapter["code"] == "AI"
    assert [(s.kind, s.label, s.text) for s in sources] == [
        ("NOTES", "Rezumatul profesorului (memorator)", "rezumat"),
        ("EN", "Culegere EN, pagina 12", "din culegere"),
        ("MANUAL", "Manual clasa a V-a, pagina 30", "din manual"),
    ]
    assert sources[1].id.startswith("EN-") and sources[2].id.startswith("MAN-")


def test_a_step_the_model_numbered_itself_is_not_numbered_twice():
    lesson = {**LESSON, "problem_types": [{**LESSON["problem_types"][0], "method": [claim("1. Înlocuim $x$.", "EN-1")]}]}

    assert "1. Înlocuim $x$." in lesson_markdown(lesson, CHAPTER) and "1. 1." not in lesson_markdown(lesson, CHAPTER)


def test_the_teachers_decisions_come_before_the_sources_and_go_with_the_draft():
    asked = request(CHAPTER, SOURCES, [])

    assert asked.text.index("<teacher_decisions>") < asked.text.index("<sources>")
    assert r"$a \neq 0$" in asked.text and "teacher_decisions" in asked.system
    draft = synthesize(lambda req, model, attempts: Asked(LESSON, model, 1, 1), Settings(), CHAPTER, SOURCES, [])
    assert any(r"a \neq 0" in d for d in draft["decisions"])


def test_latex_whose_backslash_json_ate_is_put_back():
    broken = {"text": "$f : R \to R$ și $(a, b) \neq (b, a)$\nunde $x$", "list": ["$x \notin A$"]}

    fixed = repair_claims(broken)

    assert fixed["text"] == "$f : R \\to R$ și $(a, b) \\neq (b, a)$\nunde $x$"  # a real new line stays
    assert fixed["list"] == ["$x \\notin A$"]


def test_a_revision_sends_the_draft_and_everything_not_green():
    review = {"claims": {"idea.1": {"colour": "green", "reasons": []},
                         "def1.1": {"colour": "red", "reasons": ["greșeală de matematică: $\\in$, nu $\\times$"]},
                         "cover.1": {"colour": "red", "reasons": ["lipsește din lecție: Coliniaritate (EN-1)"]}}}

    asked = revise_request(CHAPTER, SOURCES, [], {"lesson": LESSON}, review)

    assert "<draft>" in asked.text and '"term": "Funcția liniară"' in asked.text
    assert '"id": "def1.1"' in asked.text and "Coliniaritate" in asked.text and '"id": "idea.1"' not in asked.text
    assert "Return the WHOLE corrected lesson" in asked.system and asked.schema is not None


def test_a_revised_draft_counts_its_revision():
    first = synthesize(lambda req, model, attempts: Asked(LESSON, model, 1, 1), Settings(), CHAPTER, SOURCES, [])
    second = synthesize(lambda req, model, attempts: Asked(LESSON, model, 1, 1), Settings(), CHAPTER, SOURCES, [],
                        revise=(first, {"claims": {}}))

    assert (first["revision"], second["revision"]) == (0, 1)


def test_a_lesson_is_asked_of_the_strong_model_again_never_of_the_stand_in():
    seen = []

    def ask(req, model, attempts):
        seen.append(model)
        return Asked(None if len(seen) < 3 else LESSON, model, 10, 10, "Gemini a răspuns 504")

    asked = ask_strong(ask, request(CHAPTER, SOURCES, []), Settings(), pause_seconds=0)

    assert seen == [Settings().solve_model] * 3 and asked.data == LESSON and asked.input_tokens == 30
    assert ask_strong(lambda r, m, a: Asked(None, m, 1, 1, "504"), request(CHAPTER, SOURCES, []), Settings(),
                      pause_seconds=0).data is None


def test_an_empty_example_is_not_a_claim():
    lesson = {**LESSON, "definitions": [{**LESSON["definitions"][0], "example": claim("")}]}

    assert "def1.ex" not in [claim_id for claim_id, _, _ in claims(lesson)]
    assert "*Exemplu:*" not in lesson_markdown(lesson, CHAPTER)
