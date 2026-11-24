"""From the transcribed photos of a chapter page to its passages, in a throw-away database (see conftest.py): the
paid part (reading the photos) is not run here - the transcripts are already in tutor_photo_transcripts, as they are on
a second run."""

from langchain_core.embeddings import DeterministicFakeEmbedding

from tutor.build_chapter import Photo, finish, numbered_pages, store_transcribed

EMBEDDINGS = DeterministicFakeEmbedding(size=1536)


def transcript(markdown: str, book_page: int | None = None) -> str:
    """As tutor.transcribe writes a page: a small front matter (the printed page number, if one was read), then the text."""
    header = "source_pdf: poze\n" + (f"book_page: {book_page}\n" if book_page is not None else "")
    return f"---\n{header}---\n\n{markdown}\n"


def chapter(conn, code: str, *, notes: str | None = None, book_page: int | None = None) -> int:
    return conn.execute(
        "INSERT INTO en_chapters (code, title, area, position, notes, book_page) "
        "VALUES (%s, 'Ordinea efectuării operațiilor', 'ALGEBRA', 9, %s, %s) RETURNING id",
        (code, notes, book_page)).fetchone()[0]


def photos_with(conn, chapter_id: int, contents: list[str]) -> tuple[list[Photo], dict[int, str]]:
    photos, done = [], {}
    for n, content in enumerate(contents, start=1):
        photo_id = conn.execute(
            "INSERT INTO en_chapter_photos (chapter_id, position, image_key) VALUES (%s, %s, %s) RETURNING id",
            (chapter_id, n * 10, f"{n}.jpg")).fetchone()[0]  # positions with gaps, as after a deleted photo
        conn.execute("INSERT INTO tutor_photo_transcripts (photo_id, content, transcribed_with) VALUES (%s, %s, 'm')",
                     (photo_id, content))
        photos.append(Photo(photo_id, n * 10, f"{n}.jpg"))
        done[photo_id] = content
    return photos, done


def stored(conn):
    return conn.execute(
        "SELECT c.code, t.book_page, t.photo_id, t.source, t.source_name FROM tutor_chunks t "
        "JOIN en_chapters c ON c.id = t.chapter_id ORDER BY t.book_page, t.position").fetchall()


def test_photos_on_an_en_chapter_page_become_passages_of_that_page(conn):
    chapter_id = chapter(conn, "A1", notes="Textul scris de profesor.")
    photos, done = photos_with(conn, chapter_id, [
        # a notebook page: no printed number; then a page of some book, with numbering of its own and a chapter title
        transcript("## 1. Ordinea operațiilor\n\nÎntâi puterile, apoi înmulțirile și împărțirile."),
        transcript("# II. RAPOARTE\n\n## 2. Paranteze\n\nÎntâi parantezele rotunde, apoi cele pătrate.", book_page=87),
    ])

    built = store_transcribed(conn, "A1", None, photos, done, EMBEDDINGS, "m")

    assert built.problems == []
    assert built.stored.unresolved == []
    assert built.stored.inserted == 2
    # On A1 both, numbered as the lesson page numbers its photos (1, 2) - not 87, and nothing on the book's AII.
    assert stored(conn) == [
        ("A1", 1, photos[0].id, "PHOTO", "A1 (poze LMS)"),
        ("A1", 2, photos[1].id, "PHOTO", "A1 (poze LMS)"),
    ]
    # The page already has the teacher's text: it is kept.
    assert built.published.kept == ["A1"]
    assert conn.execute("SELECT notes FROM en_chapters WHERE id = %s", (chapter_id,)).fetchone()[0] == "Textul scris de profesor."


def test_the_pages_follow_the_first_page_set_on_the_chapter(conn):
    chapter_id = chapter(conn, "G3", book_page=40)
    photos, done = photos_with(conn, chapter_id, [transcript("Suma unghiurilor unui triunghi este $180^\\circ$."),
                                                  transcript("Triunghiul isoscel are două laturi congruente.")])

    store_transcribed(conn, "G3", 40, photos, done, EMBEDDINGS, "m")

    # EnChapterPage calls photo n "pagina 40 + n - 1": a citation of page 41 is the second photo.
    assert [(row[1], row[2]) for row in stored(conn)] == [(40, photos[0].id), (41, photos[1].id)]


def test_an_empty_page_gets_the_text_of_its_photos_unless_told_not_to(conn):
    chapter_id = chapter(conn, "A2")
    photos, done = photos_with(conn, chapter_id, [transcript("Raportul numerelor $a$ și $b$ este $\\frac{a}{b}$.")])

    quiet = store_transcribed(conn, "A2", None, photos, done, EMBEDDINGS, "m", publish=False)
    assert quiet.published is None
    assert conn.execute("SELECT notes FROM en_chapters WHERE id = %s", (chapter_id,)).fetchone()[0] is None

    built = store_transcribed(conn, "A2", None, photos, done, EMBEDDINGS, "m")
    assert built.published.updated == ["A2"]
    assert "Raportul numerelor" in conn.execute("SELECT notes FROM en_chapters WHERE id = %s", (chapter_id,)).fetchone()[0]
    assert (built.stored.inserted, built.stored.unchanged) == (0, 1)  # the second run embeds nothing again


def test_a_photo_not_transcribed_yet_keeps_its_place_in_the_numbering(conn):
    chapter_id = chapter(conn, "A4")
    photos, done = photos_with(conn, chapter_id, [transcript("Prima pagină."), transcript("A doua."), transcript("A treia.")])
    del done[photos[1].id]  # the cost limit stopped the run before the second photo

    store_transcribed(conn, "A4", None, photos, done, EMBEDDINGS, "m")

    assert [(row[1], row[2]) for row in stored(conn)] == [(1, photos[0].id), (3, photos[2].id)]


def test_a_chapter_of_the_book_still_follows_its_cuprins(conn):
    # conftest.py has the book's AI, AIS2 and AIS2P2: photos uploaded on AI are cut by the printed pages, as before.
    chapter_id = conn.execute("SELECT id FROM en_chapters WHERE code = 'AI'").fetchone()[0]
    photos, done = photos_with(conn, chapter_id, [
        transcript("# I. MULȚIMEA NUMERELOR NATURALE\n\n## 2. Adunarea și scăderea\n\n### 2.2. SCĂDEREA\n\nDacă $a-b=d$.", book_page=10),
    ])

    built = store_transcribed(conn, "AI", None, photos, done, EMBEDDINGS, "m")

    assert built.stored.unresolved == []
    assert stored(conn) == [("AIS2P2", 10, photos[0].id, "PHOTO", "AI (poze LMS)")]


def test_passages_of_the_book_with_no_page_in_the_lms_are_reported(conn):
    # The trap this guards: the photo was read (paid for) and its passages were stored nowhere, silently.
    chapter_id = conn.execute("SELECT id FROM en_chapters WHERE code = 'AI'").fetchone()[0]
    photos, done = photos_with(conn, chapter_id, [transcript("## 1. Fracții\n\nO fracție este $\\frac{a}{b}$.", book_page=22)])

    built = store_transcribed(conn, "AI", None, photos, done, EMBEDDINGS, "m")

    assert stored(conn) == []
    assert built.stored.unresolved == ["AIIS1"]  # page 22 is the book's chapter II, which has no page in the LMS
    assert any("AIIS1" in line for line in built.warnings())


def test_the_passages_of_an_en_page_are_what_its_exam_position_finds(conn):
    # „Propune din lecții” on a question at S I.1 looks first at the page that position is linked to: A1.
    from tutor.retrieval import slot_baseline

    chapter_id = chapter(conn, "A1", notes="Textul profesorului.")
    other_id = chapter(conn, "A10")  # a neighbour whose code starts the same: none of its passages may come along
    photos, done = photos_with(conn, chapter_id, [transcript("Întâi puterile, apoi înmulțirile."),
                                                  transcript("La urmă adunările și scăderile.")])
    other_photos, other_done = photos_with(conn, other_id, [transcript("Formula $(a+b)^2 = a^2 + 2ab + b^2$.")])
    store_transcribed(conn, "A1", None, photos, done, EMBEDDINGS, "m")
    store_transcribed(conn, "A10", None, other_photos, other_done, EMBEDDINGS, "m")
    conn.execute("CREATE TABLE IF NOT EXISTS en_chapter_slots (slot VARCHAR(12) PRIMARY KEY, "
                 "chapter_id BIGINT NOT NULL REFERENCES en_chapters (id))")
    conn.execute("INSERT INTO en_chapter_slots (slot, chapter_id) VALUES ('I.1', %s)", (chapter_id,))

    hits = slot_baseline(conn, "I.1", limit=5)

    assert [(hit.code, hit.book_page, hit.content) for hit in hits] == [
        ("A1", 1, "Întâi puterile, apoi înmulțirile."),
        ("A1", 2, "La urmă adunările și scăderile."),
    ]


def test_numbered_pages_numbers_only_a_home_page_by_position():
    photos = [Photo(7, 10, "a.jpg"), Photo(9, 20, "b.jpg")]
    done = {7: transcript("unu", book_page=87), 9: transcript("doi")}

    home, _ = numbered_pages("A1", None, photos, done, home="A1")
    assert [page.book_page for page in home] == [1, 2]

    # A chapter of the book is untouched: the printed number, else first page + the photo's position - 1.
    book, by_page = numbered_pages("AI", 9, photos, done, home=None)
    assert [page.book_page for page in book] == [87, 28]
    assert by_page == {87: 7, 28: 9}


def notes(conn, chapter_id: int):
    return conn.execute("SELECT notes FROM en_chapters WHERE id = %s", (chapter_id,)).fetchone()[0]


def test_a_chapter_with_an_unread_photo_is_neither_stored_nor_published(conn):
    chapter_id = chapter(conn, "A5")
    photos, done = photos_with(conn, chapter_id, [transcript("Prima pagină."), transcript("A doua.")])
    del done[photos[1].id]  # the file was missing, the model failed or the cost limit stopped the run

    assert finish(conn, "A5", None, photos, done, EMBEDDINGS, "m") is None
    assert stored(conn) == []
    assert notes(conn, chapter_id) is None


def test_allow_partial_stores_for_the_bot_but_writes_no_lesson_text(conn):
    chapter_id = chapter(conn, "A6")
    photos, done = photos_with(conn, chapter_id, [transcript("Prima pagină."), transcript("A doua.")])
    del done[photos[1].id]

    built = finish(conn, "A6", None, photos, done, EMBEDDINGS, "m", allow_partial=True)

    assert [row[2] for row in stored(conn)] == [photos[0].id]
    assert built.published is None
    assert notes(conn, chapter_id) is None


def test_the_whole_chapter_replaces_a_lesson_text_the_tool_wrote_from_part_of_it(conn):
    chapter_id = chapter(conn, "A7")
    photos, done = photos_with(conn, chapter_id, [transcript("Prima pagină."), transcript("A doua.")])
    store_transcribed(conn, "A7", None, photos[:1], {photos[0].id: done[photos[0].id]}, EMBEDDINGS, "m")
    assert notes(conn, chapter_id) == "Prima pagină."  # as an older run, before finish() guarded it

    built = finish(conn, "A7", None, photos, done, EMBEDDINGS, "m")

    assert built.published.updated == ["A7"]
    assert notes(conn, chapter_id) == "Prima pagină.\n\nA doua."
