from langchain_core.documents import Document

from tutor.publish import MAX_NOTES, notes_text, publish_notes


def doc(code: str, text: str, page: int = 10, position: int = 1) -> Document:
    return Document(page_content=text, metadata={
        "code": code, "kind": "THEORY", "book_page": page, "source_name": "book.pdf", "position": position})


def notes_of(conn, code: str):
    return conn.execute("SELECT notes FROM en_chapters WHERE code = %s", (code,)).fetchone()[0]


def test_the_text_drops_the_markdown_headings_because_the_app_shows_the_title():
    text = notes_text(["## 2.2. SCĂDEREA\n\nDacă $a-b=d$, atunci $a$ este descăzutul.", "**Atenție!** Nu e comutativă."])

    assert text == "Dacă $a-b=d$, atunci $a$ este descăzutul.\n\n**Atenție!** Nu e comutativă."


def test_each_section_gets_its_own_text_in_book_order(conn):
    documents = [
        doc("AIS2P2", "al doilea", page=11, position=1),
        doc("AIS2P2", "primul", page=10, position=2),
        doc("AIS2", "introducere", page=10, position=1),
    ]

    report = publish_notes(conn, documents)

    assert sorted(report.updated) == ["AIS2", "AIS2P2"]
    assert notes_of(conn, "AIS2P2") == "primul\n\nal doilea"
    assert notes_of(conn, "AIS2") == "introducere"


def test_text_the_teacher_wrote_is_kept_unless_overwrite_is_asked_for(conn):
    conn.execute("UPDATE en_chapters SET notes = 'scris de profesor' WHERE code = 'AIS2P2'")

    kept = publish_notes(conn, [doc("AIS2P2", "din poză")])
    assert kept.kept == ["AIS2P2"]
    assert notes_of(conn, "AIS2P2") == "scris de profesor"

    replaced = publish_notes(conn, [doc("AIS2P2", "din poză")], overwrite=True)
    assert replaced.updated == ["AIS2P2"]
    assert notes_of(conn, "AIS2P2") == "din poză"


def test_a_section_without_a_page_in_the_lms_falls_back_to_its_chapter(conn):
    report = publish_notes(conn, [doc("AIS9", "text fără pagină proprie")])

    assert report.updated == ["AI"]
    assert notes_of(conn, "AI") == "text fără pagină proprie"


def test_a_text_longer_than_a_page_holds_is_reported_not_cut(conn):
    report = publish_notes(conn, [doc("AIS2", "x" * (MAX_NOTES + 1))])

    assert report.too_long == ["AIS2"]
    assert notes_of(conn, "AIS2") is None
