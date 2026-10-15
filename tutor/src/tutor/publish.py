"""Writes the theory of each section as plain text into the LMS page of that section (en_chapters.notes).

That text is what students read in the app, so it is only written where the teacher has not written anything: a page
with text of its own is left alone unless `overwrite` is asked for.
"""

import re
from dataclasses import dataclass, field

import psycopg
from langchain_core.documents import Document

from tutor.store import pages_by_code, resolve

MAX_NOTES = 60_000  # EnChapter.MAX_NOTES in the backend


@dataclass
class PublishReport:
    updated: list[str] = field(default_factory=list)
    kept: list[str] = field(default_factory=list)  # had text of its own
    empty: list[str] = field(default_factory=list)
    too_long: list[str] = field(default_factory=list)
    unresolved: list[str] = field(default_factory=list)  # no page in the LMS for that code


def notes_text(contents: list[str]) -> str:
    """The chunks of one section as the text under its title: Markdown headings dropped (the app shows the title)."""
    parts: list[str] = []
    for content in contents:
        lines = [line.rstrip() for line in content.splitlines() if not line.lstrip().startswith("#")]
        part = "\n".join(lines).strip()
        if part:
            parts.append(part)
    return re.sub(r"\n{3,}", "\n\n", "\n\n".join(parts)).strip()


def publish_notes(conn: psycopg.Connection, documents: list[Document], *, overwrite: bool = False) -> PublishReport:
    """Does not commit: the caller decides."""
    report = PublishReport()
    pages = pages_by_code(conn, {d.metadata["code"] for d in documents})

    by_page: dict[str, list[Document]] = {}
    for document in documents:
        code = resolve(document.metadata["code"], pages)
        if code is None:
            report.unresolved.append(document.metadata["code"])
            continue
        by_page.setdefault(code, []).append(document)

    for code, docs in sorted(by_page.items()):
        docs.sort(key=lambda d: (d.metadata["book_page"], d.metadata["position"]))
        text = notes_text([d.page_content for d in docs])
        if text == "":
            report.empty.append(code)
        elif len(text) > MAX_NOTES:
            report.too_long.append(code)
        else:
            changed = conn.execute(
                "UPDATE en_chapters SET notes = %s WHERE id = %s AND (notes IS NULL OR %s)",
                (text, pages[code][0], overwrite),
            ).rowcount
            (report.updated if changed else report.kept).append(code)
    return report
