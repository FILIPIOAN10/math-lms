"""Pilot, step 2: cuts the transcribed pages (tutor/data/teme/page-NN.md) into numbered exercises per tema.

Writes tutor/data/teme/exercises.json: [{id, tema, number, statement, pages, has_figure}]. A tema starts at a "TEMA n"
line and its exercise numbers start again from 1; an exercise runs until the next number, across a page break.
"""

import json
import pathlib
import re

DATA = pathlib.Path(__file__).resolve().parent.parent / "data" / "teme"
TEMA = re.compile(r"^\W*TEMA\s+(\d+)\b", re.IGNORECASE)
NUMBER = re.compile(r"^\s*(?:\*\*)?(\d{1,2})\.(?:\*\*)?\s+(.*)$")
FRONT = re.compile(r"^---\n.*?\n---\n", re.DOTALL)
BOOK_PAGE = re.compile(r"^book_page:\s*(\d+)", re.MULTILINE)


def body_and_page(path: pathlib.Path) -> tuple[str, int | None]:
    text = path.read_text(encoding="utf-8")
    page = BOOK_PAGE.search(text)
    # the file starts with a heading line, then a front matter block; the transcript follows the second "---"
    parts = text.split("\n---\n")
    body = parts[-1] if len(parts) >= 3 else FRONT.sub("", text)
    return body, int(page.group(1)) if page else None


def main() -> None:
    exercises: list[dict] = []
    tema, current = 0, None
    pages = sorted(DATA.glob("page-*.md"))
    for order, path in enumerate(pages, start=1):
        body, book_page = body_and_page(path)
        for line in body.splitlines():
            match = TEMA.match(line)
            if match:
                tema, current = int(match.group(1)), None
                continue
            number = NUMBER.match(line)
            # a number must follow the previous one (1, 2, 3 ...) so "12. " inside a statement is not a new exercise
            if number and tema and int(number.group(1)) == (current["number"] + 1 if current else 1):
                current = {"id": f"T{tema}-{int(number.group(1))}", "tema": tema, "number": int(number.group(1)),
                           "statement": number.group(2), "pages": [book_page or order]}
                exercises.append(current)
                continue
            if current is not None and line.strip():
                current["statement"] += "\n" + line
                if (book_page or order) not in current["pages"]:
                    current["pages"].append(book_page or order)
    for ex in exercises:
        ex["has_figure"] = bool(re.search(r"\[figura|\(figura|\(fig\.", ex["statement"], re.IGNORECASE))
        ex["statement"] = ex["statement"].strip()
    (DATA / "exercises.json").write_text(json.dumps(exercises, ensure_ascii=False, indent=1), encoding="utf-8")
    by_tema: dict[int, int] = {}
    for ex in exercises:
        by_tema[ex["tema"]] = by_tema.get(ex["tema"], 0) + 1
    print(len(exercises), "exerciții;", ", ".join(f"T{t}: {n}" for t, n in sorted(by_tema.items())))
    print("cu figură:", sum(ex["has_figure"] for ex in exercises))


if __name__ == "__main__":
    main()
