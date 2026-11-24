"""Plan step 5: a checked lesson draft goes into the app (en_chapter_lessons, V78) for the teacher to review; nothing
reaches a student until the teacher approves it, and a text the teacher edited is never replaced."""

import json

from tutor.store_lesson import review_claims, store_lesson

CHAPTER = {"id": 999, "code": "AIS2", "title": "Adunarea și scăderea"}


def claim(text: str, *sources: str) -> dict:
    return {"text": text, "source_ids": list(sources), "own": not sources, "conflict": "", "checks": []}


LESSON = {"prerequisites": [], "idea": [claim("Adunăm $2 + 3 = 5$.", "EN-1")], "definitions": [],
          "formulas": [claim("$a + b = b + a$", "MAN-2")], "problem_types": [], "mistakes": [], "recap": []}
DRAFT = {"chapter": CHAPTER, "model": "gemini-x", "lesson": LESSON,
         "sources": [{"id": "EN-1", "kind": "EN", "label": "Culegere EN, pagina 3", "text": "2 + 3 = 5"},
                     {"id": "MAN-2", "kind": "MANUAL", "label": "Manual, pagina 9", "text": "comutativitate"},
                     {"id": "EN-7", "kind": "EN", "label": "Culegere EN, pagina 4", "text": "nefolosit"}]}


def review(red: bool = False) -> dict:
    claims = {"idea.1": {"colour": "green", "reasons": []},
              "formula.1": {"colour": "red" if red else "yellow", "reasons": ["lipsește o condiție"]}}
    return {"status": "flagged" if red else "draft", "claims": claims,
            "counts": {"green": 1, "yellow": 0 if red else 1, "red": 1 if red else 0}}


def row(conn):
    return conn.execute("SELECT markdown, claims_json, sources_json, status, red_count, yellow_count, "
                        "edited_by_teacher, model FROM en_chapter_lessons l JOIN en_chapters c ON c.id = l.chapter_id "
                        "WHERE c.code = 'AIS2'").fetchone()


def test_the_review_lists_each_claim_with_its_colour_and_the_gaps_first():
    gaps = {**review()["claims"], "cover.1": {"colour": "red", "reasons": ["lipsește din lecție: Scăderea"]}}

    listed = review_claims(DRAFT, {"claims": gaps})

    assert [c["id"] for c in listed] == ["cover.1", "idea.1", "formula.1"]
    assert listed[1] == {"id": "idea.1", "where": "Ideea pe scurt", "text": "Adunăm $2 + 3 = 5$.", "colour": "green",
                         "reasons": [], "sourceIds": ["EN-1"]}
    assert listed[0]["where"] == "Ce lipsește din lecție"


def test_a_checked_draft_is_stored_for_the_teacher_and_hides_the_chapter(conn):
    conn.execute("UPDATE en_chapters SET lesson_published = TRUE WHERE code = 'AIS2'")

    assert store_lesson(conn, DRAFT, review(red=True)) == "written"

    markdown, claims_json, sources_json, status, red, yellow, edited, model = row(conn)
    assert markdown.startswith("## Ce trebuie să știi înainte") and "Adunăm $2 + 3 = 5$." in markdown
    assert status == "flagged" and (red, yellow, edited, model) == (1, 0, False, "gemini-x")
    assert [c["id"] for c in json.loads(claims_json)] == ["idea.1", "formula.1"]
    assert [s["id"] for s in json.loads(sources_json)] == ["EN-1", "MAN-2"]  # only what the claims cite
    assert conn.execute("SELECT lesson_published FROM en_chapters WHERE code = 'AIS2'").fetchone()[0] is False


def test_a_new_draft_replaces_an_approved_one_and_takes_the_approval_away(conn):
    store_lesson(conn, DRAFT, review(red=True))
    conn.execute("UPDATE en_chapter_lessons SET status = 'approved', approved_at = now()")

    store_lesson(conn, DRAFT, review(red=False))

    assert row(conn)[3] == "draft"
    assert conn.execute("SELECT approved_at FROM en_chapter_lessons").fetchone()[0] is None


def test_a_text_the_teacher_edited_is_kept_unless_overwrite_is_asked_for(conn):
    store_lesson(conn, DRAFT, review())
    conn.execute("UPDATE en_chapter_lessons SET markdown = 'scris de profesor', edited_by_teacher = TRUE")

    assert store_lesson(conn, DRAFT, review()) == "kept"
    assert row(conn)[0] == "scris de profesor"

    assert store_lesson(conn, DRAFT, review(), overwrite=True) == "written"
    assert row(conn)[0] != "scris de profesor" and row(conn)[6] is False
