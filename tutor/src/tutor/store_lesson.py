"""Puts a checked lesson draft into the app (plan step 5, en_chapter_lessons, V78), for the teacher to review on the
chapter page. Students read it only once the teacher approves it AND shows the chapter, so storing a draft also hides
the chapter again (the approval was for the text the teacher read) and drops any earlier approval.

A text the teacher edited in the app is theirs: it is kept unless --overwrite is asked for.

    uv run python -X utf8 -m tutor.store_lesson --chapter A11 [--overwrite]

Reads data/lessons/<code>/draft.json and review.json (tutor.synthesize, then tutor.verify_lesson). The chapter is
found by its code, so a draft made on one database can be stored in another.
"""

import argparse
import hashlib
import json
import re

import psycopg

from tutor.config import get_settings
from tutor.publish import hide_lessons
from tutor.synthesize import LESSONS, claims, lesson_markdown

_GAPS = "Ce lipsește din lecție"
_PREREQUISITES = "Ce trebuie să știi înainte"


def review_claims(draft: dict, review: dict) -> list[dict]:
    """Every claim with its colour, in reading order, the gaps and the wrong prerequisites first."""
    entries = review.get("claims") or {}
    out = [{"id": claim_id, "where": _GAPS if claim_id.startswith("cover.") else _PREREQUISITES, "text": "",
            "colour": entry.get("colour", "red"), "reasons": list(entry.get("reasons") or []), "sourceIds": []}
           for claim_id, entry in entries.items() if claim_id.startswith(("cover.", "pre."))]
    for claim_id, where, claim in claims(draft["lesson"]):
        entry = entries.get(claim_id) or {"colour": "yellow", "reasons": ["neverificat"]}
        out.append({"id": claim_id, "where": where, "text": str(claim.get("text") or ""),
                    "colour": entry.get("colour", "yellow"), "reasons": list(entry.get("reasons") or []),
                    "sourceIds": [str(s) for s in claim.get("source_ids") or []]})
    return out


def student_markdown(draft: dict) -> str:
    """The lesson without its title line: the chapter page shows the title."""
    return re.sub(r"^# .*\n+", "", lesson_markdown(draft["lesson"], draft["chapter"]), count=1)


def store_lesson(conn: psycopg.Connection, draft: dict, review: dict, *, overwrite: bool = False) -> str:
    """"written" or "kept" (the teacher's edited text). Does not commit: the caller decides."""
    found = conn.execute("SELECT id FROM en_chapters WHERE code = %s", (draft["chapter"]["code"],)).fetchone()
    if found is None:
        raise SystemExit(f"Nu există capitolul {draft['chapter']['code']} în această bază de date.")
    chapter_id = found[0]
    edited = conn.execute("SELECT edited_by_teacher FROM en_chapter_lessons WHERE chapter_id = %s",
                          (chapter_id,)).fetchone()
    if edited and edited[0] and not overwrite:
        return "kept"
    listed = review_claims(draft, review)
    cited = {s for c in listed for s in c["sourceIds"]}
    sources = [s for s in draft["sources"] if s["id"] in cited]
    markdown = student_markdown(draft)
    red = sum(1 for c in listed if c["colour"] == "red")
    yellow = sum(1 for c in listed if c["colour"] == "yellow")
    conn.execute(
        """INSERT INTO en_chapter_lessons (chapter_id, markdown, claims_json, sources_json, status, red_count,
               yellow_count, edited_by_teacher, generated_md5, model, approved_by, approved_at, updated_at)
           VALUES (%s, %s, %s, %s, %s, %s, %s, FALSE, %s, %s, NULL, NULL, now())
           ON CONFLICT (chapter_id) DO UPDATE SET markdown = EXCLUDED.markdown, claims_json = EXCLUDED.claims_json,
               sources_json = EXCLUDED.sources_json, status = EXCLUDED.status, red_count = EXCLUDED.red_count,
               yellow_count = EXCLUDED.yellow_count, edited_by_teacher = FALSE,
               generated_md5 = EXCLUDED.generated_md5, model = EXCLUDED.model, approved_by = NULL,
               approved_at = NULL, updated_at = now()""",
        (chapter_id, markdown, json.dumps(listed, ensure_ascii=False), json.dumps(sources, ensure_ascii=False),
         "flagged" if red else "draft", red, yellow, hashlib.md5(markdown.encode("utf-8")).hexdigest(),
         str(draft.get("model") or "")[:60]))
    hide_lessons(conn, {chapter_id})
    return "written"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--chapter", required=True)
    parser.add_argument("--overwrite", action="store_true", help="replace a text the teacher edited in the app")
    args = parser.parse_args()
    folder = LESSONS / args.chapter.strip().upper()
    draft = json.loads((folder / "draft.json").read_text(encoding="utf-8"))
    review = json.loads((folder / "review.json").read_text(encoding="utf-8"))
    with psycopg.connect(get_settings().psycopg_dsn) as conn:
        result = store_lesson(conn, draft, review, overwrite=args.overwrite)
        conn.commit()
    print(f"{draft['chapter']['code']}: {'scrisă' if result == 'written' else 'păstrată (editată de profesor)'}, "
          f"{review.get('counts')}")


if __name__ == "__main__":
    main()
