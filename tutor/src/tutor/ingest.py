"""Turns transcribed pages (data/theory/<pdf>/pNN.md) into chunks and stores them with embeddings.

Run:  uv run python -m tutor.ingest data/theory/I._MULTIMEA [--dry-run]
"""

import argparse
import pathlib
import sys

import psycopg
import tiktoken

from tutor import chunking
from tutor.config import get_settings
from tutor.llm import embeddings
from tutor.store import store_chunks

PRICE_PER_MILLION = 0.02  # text-embedding-3-small, USD per million tokens


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Taie paginile transcrise în chunk-uri și le pune în pgvector.")
    parser.add_argument("folder", type=pathlib.Path, help="folderul cu pNN.md (data/theory/<pdf>)")
    parser.add_argument("--dry-run", action="store_true", help="arată ce ar intra, fără embeddings și fără scriere")
    args = parser.parse_args(argv)

    files = sorted(args.folder.glob("p*.md"))
    if not files:
        print(f"Niciun pNN.md în {args.folder}")
        return 1
    pages = [chunking.read_transcript(f) for f in files]
    documents, problems = chunking.chunk_pages(pages)
    source_name = pages[0].source_name

    print(f"{source_name}: {len(pages)} pagini -> {len(documents)} chunk-uri")
    by_code: dict[str, list[int]] = {}
    for document in documents:
        by_code.setdefault(document.metadata["code"], []).append(len(document.page_content))
    for code, sizes in sorted(by_code.items()):
        print(f"  {code:<10} {len(sizes)} chunk-uri, {sum(sizes)} caractere")
    for problem in problems:
        print("  ATENȚIE:", problem)

    encoder = tiktoken.get_encoding("cl100k_base")
    tokens = sum(len(encoder.encode(d.page_content)) for d in documents)
    print(f"Embeddings: ~{tokens} tokeni, ~{tokens * PRICE_PER_MILLION / 1_000_000:.5f} USD")
    if args.dry_run:
        print("Dry-run: nu s-a scris nimic.")
        return 0

    settings = get_settings()
    with psycopg.connect(settings.psycopg_dsn) as conn:
        report = store_chunks(conn, documents, embeddings(settings), settings.embedding_model, source_name)
        conn.commit()
    print(f"Scrise: {report.inserted} noi, {report.unchanged} neschimbate, {report.removed} șterse (vechi).")
    for code in report.unresolved:
        print(f"  fără pagină în LMS pentru {code}, sărit")
    return 0


if __name__ == "__main__":
    sys.exit(main())
