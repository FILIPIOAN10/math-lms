"""Finding the passages of the book that explain an exercise: four ways, so they can be compared on real questions.

- vector:   nearest chunks by meaning (cosine distance in pgvector)
- fulltext: Postgres full-text search with the Romanian stemmer, any word of the question may match (no IDF)
- bm25:     BM25 over Romanian stems: a word that is in every chunk ("numere", "naturale") counts for little
- hybrid:   vector and BM25 rankings fused by reciprocal rank fusion (RRF); hybrid_2_1 counts the vector ranking twice
- mmr:      vector, but each next chunk must also differ from those already chosen (no five copies of one idea)

Plus the baseline the platform already has: the exam position of a question (I.1, III.5.b, ...) names a chapter page,
so every chunk under that page is a candidate - no search at all.

`ChunkRetriever` is a LangChain retriever, so any strategy plugs into a chain unchanged.
"""

import math
import re
import unicodedata
from collections import Counter
from dataclasses import dataclass

import numpy as np
import psycopg
import snowballstemmer
from langchain_core.callbacks import CallbackManagerForRetrieverRun
from langchain_core.documents import Document
from langchain_core.embeddings import Embeddings
from langchain_core.retrievers import BaseRetriever
from pgvector.psycopg import Vector, register_vector
from pydantic import ConfigDict

from tutor import page_map

STRATEGIES = ("vector", "fulltext", "bm25", "hybrid", "hybrid_2_1", "mmr")
RRF_K = 60  # the usual constant: how fast the weight of a lower rank falls off
MMR_LAMBDA = 0.7  # 1.0 = pure relevance, 0.0 = pure diversity


@dataclass(frozen=True)
class Hit:
    chunk_id: int
    code: str
    book_page: int
    content: str
    score: float  # vector: cosine distance (lower is closer); fulltext: ts_rank (higher is better); hybrid: RRF sum


def under(code: str, page: str) -> bool:
    """Is chunk `code` the page itself or somewhere beneath it (AIS5P5 is under AIS5, AII is not under AI)?"""
    return page in page_map.parents(code)


def _codes_under(conn: psycopg.Connection, page: str | None) -> list[str] | None:
    if page is None:
        return None
    rows = conn.execute("SELECT DISTINCT c.code FROM tutor_chunks t JOIN en_chapters c ON c.id = t.chapter_id").fetchall()
    return [code for (code,) in rows if under(code, page)]


def vector_hits(conn: psycopg.Connection, vector: list[float], limit: int, only: list[str] | None = None) -> list[Hit]:
    register_vector(conn)
    rows = conn.execute(
        "SELECT t.id, c.code, t.book_page, t.content, t.embedding <=> %(q)s AS distance "
        "FROM tutor_chunks t JOIN en_chapters c ON c.id = t.chapter_id "
        "WHERE (%(only)s::text[] IS NULL OR c.code = ANY(%(only)s)) ORDER BY distance LIMIT %(n)s",
        {"q": Vector(vector), "only": only, "n": limit},
    ).fetchall()
    return [Hit(i, code, page, content, float(distance)) for i, code, page, content, distance in rows]


def fulltext_hits(conn: psycopg.Connection, question: str, limit: int, only: list[str] | None = None) -> list[Hit]:
    """Any stemmed word of the question may match (OR), ranked by how often and how close together they occur."""
    rows = conn.execute(
        "WITH q AS (SELECT replace(plainto_tsquery('romanian', %(question)s)::text, ' & ', ' | ')::tsquery AS query) "
        "SELECT t.id, c.code, t.book_page, t.content, ts_rank_cd(to_tsvector('romanian', t.content), q.query) AS score "
        "FROM tutor_chunks t JOIN en_chapters c ON c.id = t.chapter_id, q "
        "WHERE to_tsvector('romanian', t.content) @@ q.query "
        "AND (%(only)s::text[] IS NULL OR c.code = ANY(%(only)s)) ORDER BY score DESC LIMIT %(n)s",
        {"question": question, "only": only, "n": limit},
    ).fetchall()
    return [Hit(i, code, page, content, float(score)) for i, code, page, content, score in rows]


_STEMMER = snowballstemmer.stemmer("romanian")
_FORMULA = re.compile(r"\$[^$]*\$")
_STOPWORDS = frozenset(
    "cum ce care este sunt un o de la si in pe cu din pentru ca sa se mai dar sau ori ale al a ai lui sale".split())


def tokens(text: str) -> list[str]:
    """Words of a text as Romanian stems, without diacritics (scăderea, scădere and scad all become 'scad')."""
    without_formulas = _FORMULA.sub(" ", text.lower())
    out = []
    for word in re.findall(r"[^\W_]+", without_formulas):
        plain = "".join(c for c in unicodedata.normalize("NFD", word) if unicodedata.category(c) != "Mn")
        if len(plain) > 1 and plain not in _STOPWORDS:
            out.append(_STEMMER.stemWord(plain))
    return out


BM25_K1 = 1.5  # how fast repeating a word stops adding to the score
BM25_B = 0.75  # how much a long chunk is held against it


def bm25_hits(conn: psycopg.Connection, question: str, limit: int, only: list[str] | None = None) -> list[Hit]:
    """
    Okapi BM25 over the whole book (the corpus is a few hundred chunks, so it is built per question and cannot go
    stale). Word frequencies are counted over every chunk even when `only` narrows the candidates, so a word's
    weight does not change with the filter. Written out instead of a library because the common one gives every
    word weight 0 on a corpus of two chunks (IDF = log(1) = 0), which is exactly what a narrow filter produces.
    """
    rows = conn.execute(
        "SELECT t.id, c.code, t.book_page, t.content FROM tutor_chunks t JOIN en_chapters c ON c.id = t.chapter_id "
        "ORDER BY t.id").fetchall()
    query = tokens(question)
    if not rows or not query:
        return []
    documents = [tokens(content) for _, _, _, content in rows]
    average = sum(len(d) for d in documents) / len(documents) or 1.0
    frequency = Counter(word for d in documents for word in set(d))

    def idf(word: str) -> float:
        n = frequency.get(word, 0)
        return math.log(1 + (len(documents) - n + 0.5) / (n + 0.5))  # always positive, even for a very common word

    scored = []
    for (chunk_id, code, page, content), words in zip(rows, documents, strict=True):
        if only is not None and code not in only:
            continue
        counts = Counter(words)
        score = sum(
            idf(w) * counts[w] * (BM25_K1 + 1) / (counts[w] + BM25_K1 * (1 - BM25_B + BM25_B * len(words) / average))
            for w in query if counts[w])
        if score > 0:
            scored.append(Hit(chunk_id, code, page, content, score))
    return sorted(scored, key=lambda h: h.score, reverse=True)[:limit]


def reciprocal_rank_fusion(rankings: list[list[Hit]], k: int = RRF_K, weights: tuple[float, ...] | None = None) -> list[Hit]:
    """
    A chunk's score is the sum of weight/(k + rank) over the rankings it appears in; no scale of any ranker matters.
    `weights` lets one ranker count more (the same, 1 each, by default).
    """
    fused: dict[int, float] = {}
    by_id: dict[int, Hit] = {}
    for ranking, weight in zip(rankings, weights or (1.0,) * len(rankings), strict=True):
        for rank, hit in enumerate(ranking, start=1):
            fused[hit.chunk_id] = fused.get(hit.chunk_id, 0.0) + weight / (k + rank)
            by_id[hit.chunk_id] = hit
    ordered = sorted(fused, key=lambda chunk_id: fused[chunk_id], reverse=True)
    return [Hit(h.chunk_id, h.code, h.book_page, h.content, fused[h.chunk_id]) for h in (by_id[i] for i in ordered)]


def max_marginal_relevance(query: np.ndarray, candidates: list[tuple[Hit, np.ndarray]], k: int,
                           lambda_: float = MMR_LAMBDA) -> list[Hit]:
    """Greedy: the next chunk is the one closest to the question that is least like what was already picked."""

    def cosine(a: np.ndarray, b: np.ndarray) -> float:
        return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b)))

    remaining = list(candidates)
    chosen: list[tuple[Hit, np.ndarray]] = []
    while remaining and len(chosen) < k:
        def value(item: tuple[Hit, np.ndarray]) -> float:
            relevance = cosine(query, item[1])
            redundancy = max((cosine(item[1], picked[1]) for picked in chosen), default=0.0)
            return lambda_ * relevance - (1 - lambda_) * redundancy

        best = max(remaining, key=value)
        remaining.remove(best)
        chosen.append(best)
    return [hit for hit, _ in chosen]


def slot_baseline(conn: psycopg.Connection, slot: str, limit: int) -> list[Hit]:
    """The platform's own rule: the page the exam position links to, and every chunk under it, in book order."""
    row = conn.execute(
        "SELECT c.code FROM en_chapter_slots s JOIN en_chapters c ON c.id = s.chapter_id WHERE s.slot = %s", (slot,)
    ).fetchone()
    if row is None:
        return []
    rows = conn.execute(
        "SELECT t.id, c.code, t.book_page, t.content FROM tutor_chunks t JOIN en_chapters c ON c.id = t.chapter_id "
        "ORDER BY t.book_page, t.position").fetchall()
    hits = [Hit(i, code, page, content, 0.0) for i, code, page, content in rows if under(code, row[0])]
    return hits[:limit]


def search(conn: psycopg.Connection, strategy: str, question: str, embeddings: Embeddings, k: int = 4,
           pool: int = 20, only_under: str | None = None, query_vector: list[float] | None = None) -> list[Hit]:
    """`query_vector` lets a caller that already embedded the question (the evaluation does it in one batch) skip it."""
    if strategy not in STRATEGIES:
        raise ValueError(f"strategie necunoscută: {strategy!r} (una din {', '.join(STRATEGIES)})")
    only = _codes_under(conn, only_under)
    if only is not None and not only:
        return []
    if strategy == "fulltext":
        return fulltext_hits(conn, question, k, only)
    if strategy == "bm25":
        return bm25_hits(conn, question, k, only)

    vector = query_vector if query_vector is not None else embeddings.embed_query(question)
    if strategy == "vector":
        return vector_hits(conn, vector, k, only)
    if strategy in ("hybrid", "hybrid_2_1"):
        weights = (1.0, 1.0) if strategy == "hybrid" else (2.0, 1.0)  # hybrid_2_1: meaning counts twice the words
        rankings = [vector_hits(conn, vector, pool, only), bm25_hits(conn, question, pool, only)]
        return reciprocal_rank_fusion(rankings, weights=weights)[:k]

    # mmr: fetch the nearest `pool` with their vectors, then pick k that are relevant and different from each other
    register_vector(conn)
    rows = conn.execute(
        "SELECT t.id, c.code, t.book_page, t.content, t.embedding <=> %(q)s AS distance, t.embedding "
        "FROM tutor_chunks t JOIN en_chapters c ON c.id = t.chapter_id "
        "WHERE (%(only)s::text[] IS NULL OR c.code = ANY(%(only)s)) ORDER BY distance LIMIT %(n)s",
        {"q": Vector(vector), "only": only, "n": pool},
    ).fetchall()
    candidates = [(Hit(i, code, page, content, float(distance)), np.asarray(embedding, dtype=float))
                  for i, code, page, content, distance, embedding in rows]
    return max_marginal_relevance(np.asarray(vector, dtype=float), candidates, k)


@dataclass(frozen=True)
class Retrieval:
    """What the help chain gets for an exercise: the passages, how they were found and, if none, why."""

    strategy: str  # "slot" (the exam position names the page) or "vector" (nearest by meaning)
    hits: list[Hit]
    best_distance: float | None  # vector only; for "slot" the platform's own link is the evidence
    reason: str | None = None  # None = usable; "no_passages" = nothing stored; "too_far" = nothing close enough

    @property
    def usable(self) -> bool:
        return self.reason is None and bool(self.hits)


def retrieve_for_exercise(conn: psycopg.Connection, statement: str, embeddings: Embeddings, slot: str | None = None,
                          k: int = 4, max_distance: float = 0.46) -> Retrieval:
    """
    The policy measured in eval/report.md: when the exercise has a place in the exam paper (I.1, III.5.b, ...) the
    page linked to it decides, with no search; otherwise the nearest chunks by meaning, refused as a whole when even
    the nearest is farther than `max_distance`. A slot whose page has no text yet falls back to the search.
    """
    if slot:
        hits = slot_baseline(conn, slot, k)
        if hits:
            return Retrieval("slot", hits, None)
    hits = search(conn, "vector", statement, embeddings, k=k)
    if not hits:
        return Retrieval("vector", [], None, "no_passages")
    best = hits[0].score
    return Retrieval("vector", hits, best, "too_far" if best > max_distance else None)


class ChunkRetriever(BaseRetriever):
    """A LangChain retriever over the chunks of the book; `strategy` is one of STRATEGIES."""

    model_config = ConfigDict(arbitrary_types_allowed=True)

    dsn: str
    embeddings: Embeddings
    strategy: str = "hybrid"
    k: int = 4
    pool: int = 20
    #: only chunks under this chapter page (for example the page an exam position links to); None = the whole book
    only_under: str | None = None

    def _get_relevant_documents(self, query: str, *, run_manager: CallbackManagerForRetrieverRun) -> list[Document]:
        with psycopg.connect(self.dsn) as conn:
            hits = search(conn, self.strategy, query, self.embeddings, self.k, self.pool, self.only_under)
        return [
            Document(page_content=h.content, metadata={"code": h.code, "book_page": h.book_page, "score": h.score,
                                                       "chunk_id": h.chunk_id})
            for h in hits
        ]
