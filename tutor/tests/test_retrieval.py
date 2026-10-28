import numpy as np
from langchain_core.documents import Document
from langchain_core.embeddings import DeterministicFakeEmbedding

from tutor.evaluate import best_threshold, first_hit_rank, is_hit, metrics
from tutor.retrieval import (
    Hit,
    max_marginal_relevance,
    reciprocal_rank_fusion,
    retrieve_for_exercise,
    search,
    slot_baseline,
    tokens,
    under,
)
from tutor.store import store_chunks

EMBEDDINGS = DeterministicFakeEmbedding(size=1536)


def hit(chunk_id: int, code: str = "AI", score: float = 0.0) -> Hit:
    return Hit(chunk_id, code, 9, f"chunk {chunk_id}", score)


def doc(code: str, text: str, page: int = 10, position: int = 1) -> Document:
    return Document(page_content=text, metadata={
        "code": code, "kind": "THEORY", "book_page": page, "source_name": "book.pdf", "position": position})


def test_a_chunk_is_under_its_own_page_and_the_ones_above_it_but_not_a_neighbour_chapter():
    assert under("AIS5P5", "AIS5")
    assert under("AIS5P5", "AI")
    assert under("AIS5P5", "AIS5P5")
    assert not under("AII", "AI")  # AII is another chapter, even though its name starts with AI
    assert not under("AIS5", "AIS5P5")


def test_a_chunk_both_rankers_like_beats_one_only_one_of_them_ranks_first():
    vector = [hit(1), hit(2), hit(3)]
    fulltext = [hit(4), hit(2)]

    fused = reciprocal_rank_fusion([vector, fulltext])

    assert [h.chunk_id for h in fused][0] == 2  # second in both beats first in just one
    assert {h.chunk_id for h in fused} == {1, 2, 3, 4}


def test_mmr_takes_the_best_and_then_something_different_from_it():
    query = np.array([1.0, 0.0])
    best = (hit(1), np.array([1.0, 0.0]))
    twin = (hit(2), np.array([0.99, 0.14]))  # nearly the same direction as the best
    different = (hit(3), np.array([0.5, 0.866]))  # less relevant, but says something else

    chosen = max_marginal_relevance(query, [best, twin, different], k=2, lambda_=0.3)

    assert [h.chunk_id for h in chosen] == [1, 3]
    # with relevance only, the twin would have been second
    assert [h.chunk_id for h in max_marginal_relevance(query, [best, twin, different], k=2, lambda_=1.0)] == [1, 2]


def add_pages(conn, *codes: str) -> None:
    """The chunks of a code are stored on the nearest page that exists, so the pages go in first."""
    for position, code in enumerate(codes, start=50):
        conn.execute("INSERT INTO en_chapters (code, title, area, position) VALUES (%s, %s, 'ALGEBRA', %s)",
                     (code, f"Pagina {code}", position))


def test_the_full_text_search_stems_romanian_words(conn):
    store_chunks(conn, [doc("AIS2P2", "Scăderea numerelor naturale: descăzut, scăzător, diferență.", position=1),
                        doc("AIS5P2", "Compararea puterilor cu aceeași bază.", position=2)],
                 EMBEDDINGS, "m", "book.pdf")

    hits = search(conn, "fulltext", "Cum scad două numere?", EMBEDDINGS, k=3)

    assert [h.code for h in hits][:1] == ["AIS2P2"]  # scad / scădere / scăderea share a stem


def test_words_are_compared_by_their_romanian_stem_without_diacritics():
    assert tokens("Scăderea numerelor, scădere și scad") == ["scad", "numer", "scad", "scad"]
    assert tokens("Cum înmulțesc și împart?") == ["inmult", "impart"]  # question words and formulas are dropped
    assert tokens("Suma $a+b=s$ este suma") == ["sum", "sum"]


def test_bm25_prefers_the_chunk_with_the_rare_words_over_the_one_with_the_common_ones(conn):
    add_pages(conn, "AIS5P2")
    store_chunks(conn, [doc("AIS2P2", "Numere naturale. Numere naturale. Numere naturale despre scădere.", position=1),
                        doc("AIS5P2", "Compararea puterilor: numere naturale cu aceeași bază.", position=2)],
                 EMBEDDINGS, "m", "book.pdf")

    hits = search(conn, "bm25", "Cum compar puterile numerelor naturale?", EMBEDDINGS, k=2)

    assert hits[0].code == "AIS5P2"  # "compar" and "puter" are rare; "numere naturale" is in both
    # a filter that leaves two chunks must not zero the scores (the frequencies are those of the whole book)
    assert [h.code for h in search(conn, "bm25", "puterile", EMBEDDINGS, k=2, only_under="AIS5")] == ["AIS5P2"]


def test_every_strategy_answers_and_the_page_filter_keeps_only_what_is_beneath_it(conn):
    add_pages(conn, "AIS5", "AIS5P2", "AIS5P5")
    store_chunks(conn, [doc("AIS2P2", "Scăderea numerelor", position=1), doc("AIS5P2", "Compararea puterilor", position=2),
                        doc("AIS5P5", "Ordinea efectuării operațiilor", position=3)],
                 EMBEDDINGS, "m", "book.pdf")

    for strategy in ("vector", "fulltext", "bm25", "hybrid", "hybrid_2_1", "mmr"):
        assert isinstance(search(conn, strategy, "puterilor", EMBEDDINGS, k=2), list)

    inside = search(conn, "vector", "orice", EMBEDDINGS, k=5, only_under="AIS5")
    assert {h.code for h in inside} == {"AIS5P2", "AIS5P5"}
    assert search(conn, "vector", "orice", EMBEDDINGS, k=5, only_under="AIX") == []


def test_the_slot_baseline_returns_everything_under_the_linked_page_in_book_order(conn):
    add_pages(conn, "AIS5P5")
    store_chunks(conn, [doc("AIS5P5", "al doilea", page=11, position=1), doc("AIS5P5", "primul", page=10, position=2),
                        doc("AIS2P2", "altceva", page=10, position=3)],
                 EMBEDDINGS, "m", "book.pdf")
    conn.execute("CREATE TABLE IF NOT EXISTS en_chapter_slots (slot VARCHAR(12) PRIMARY KEY, "
                 "chapter_id BIGINT NOT NULL REFERENCES en_chapters (id))")
    conn.execute("INSERT INTO en_chapter_slots (slot, chapter_id) SELECT 'I.1', id FROM en_chapters WHERE code = 'AIS5P5'")

    hits = slot_baseline(conn, "I.1", limit=5)

    assert [h.content for h in hits] == ["primul", "al doilea"]
    assert slot_baseline(conn, "II.9", limit=5) == []  # no page linked to that place


def test_an_exam_position_whose_page_has_no_text_yet_falls_back_to_the_search(conn):
    add_pages(conn, "AIS5P5")
    store_chunks(conn, [doc("AIS2P2", "Scăderea numerelor", position=1)], EMBEDDINGS, "m", "book.pdf")
    conn.execute("CREATE TABLE IF NOT EXISTS en_chapter_slots (slot VARCHAR(12) PRIMARY KEY, "
                 "chapter_id BIGINT NOT NULL REFERENCES en_chapters (id))")
    conn.execute("INSERT INTO en_chapter_slots (slot, chapter_id) SELECT 'I.1', id FROM en_chapters WHERE code = 'AIS5P5'")

    retrieval = retrieve_for_exercise(conn, "Scăderea numerelor", EMBEDDINGS, slot="I.1", max_distance=2.0)

    assert retrieval.strategy == "vector" and [h.code for h in retrieval.hits] == ["AIS2P2"]  # AIS5P5 has no chunks


def test_a_book_with_nothing_stored_gives_no_passages(conn):
    retrieval = retrieve_for_exercise(conn, "orice", EMBEDDINGS)

    assert retrieval.reason == "no_passages" and not retrieval.usable and retrieval.hits == []


def test_a_hit_is_the_gold_section_or_anything_beneath_it():
    assert is_hit("AIS5P5", ("AIS5",))
    assert is_hit("AIS5P5", ("AIS5P5",))
    assert not is_hit("AIS5", ("AIS5P5",))  # the section's intro does not answer a question about one point
    assert first_hit_rank([hit(1, "AIS2"), hit(2, "AIS5P5")], ("AIS5P5",)) == 2
    assert first_hit_rank([hit(1, "AIS2")], ("AIS5P5",)) is None


def test_metrics_count_hits_at_each_depth_and_the_mean_reciprocal_rank():
    result = metrics([1, 3, None, 2], returned=[5, 5, 5, 5])

    assert result.hit == {1: 0.25, 3: 0.75, 5: 0.75}
    assert result.mrr == (1 + 1 / 3 + 0 + 1 / 2) / 4


def test_the_best_threshold_separates_questions_the_book_answers_from_those_it_does_not():
    threshold, accuracy = best_threshold(positive=[0.25, 0.30, 0.40], negative=[0.55, 0.60])

    assert 0.40 <= threshold < 0.55
    assert accuracy == 1.0
