"""Every lesson page as text + our drawings, next to the book photo: what a chapter page could show instead of photos.

The text is the transcript every photo already has (tutor_photo_transcripts, Markdown with LaTeX); each [figura N] in
it becomes the drawing of `pilot_figures_redraw.py` for that page (data/theory/redraw-all, with the second look when it
ran). A figure that could not be drawn says what it is. No model is called: nothing here costs tokens.

    uv run python -X utf8 scripts/clean_lessons.py [--redraw redraw-all]

Writes tutor/data/theory/lessons/index.html and one page per chapter (git-ignored: the book is a commercial one).
"""

import argparse
import base64
import collections
import html
import json
import pathlib
import re

import psycopg

from tutor import figures
from tutor.answer_check import repair_page_latex
from tutor.config import get_settings
from tutor.transcribe import prepare

THEORY = pathlib.Path(__file__).resolve().parent.parent / "data" / "theory"
MARKER = re.compile(r"\[\s*figura\s+(\d+)\s*\]", re.IGNORECASE)
readable = repair_page_latex  # stored transcripts are repaired too (repair_lessons_latex.py); this keeps old dumps right

PAGE_SCRIPT = """
<script src="https://cdn.jsdelivr.net/npm/marked@12.0.2/marked.min.js"></script>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
<script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
<script>
// Markdown with LaTeX: the formulas are put aside before Markdown runs (it would eat their _ and \\), then drawn by KaTeX.
document.querySelectorAll("textarea.md").forEach(function (source) {
  var maths = [];
  var text = source.value.replace(/\\$\\$([\\s\\S]+?)\\$\\$|\\$([^$\\n]+?)\\$/g, function (m, block, inline) {
    maths.push([block !== undefined, block !== undefined ? block : inline]);
    return "@@MATH" + (maths.length - 1) + "@@";
  });
  var out = document.createElement("div");
  out.className = "lesson";
  out.innerHTML = marked.parse(text).replace(/@@MATH(\\d+)@@/g, function (m, i) {
    try { return katex.renderToString(maths[+i][1], {displayMode: maths[+i][0], throwOnError: false}); }
    catch (e) { return maths[+i][1]; }
  }).replace(/@@FIG(\\d+)@@/g, function (m, n) {
    var fig = document.getElementById(source.dataset.page + "-" + n);
    return fig ? fig.innerHTML : "";
  });
  source.parentNode.insertBefore(out, source);
});
</script>"""

STYLE = """<style>body{font:16px/1.55 system-ui,sans-serif;max-width:1400px;margin:0 auto;padding:16px;background:#fff;
color:#111}section{border-top:2px solid #ddd;padding:16px 0}.row{display:flex;gap:22px;flex-wrap:wrap;
align-items:flex-start}.book{flex:0 1 460px}.book img{width:100%;border:1px solid #ccc}.lesson{flex:1 1 520px;
max-width:760px}.lesson img{max-width:100%;max-height:380px;display:block;margin:10px auto}.lesson table{border-collapse:
collapse}.lesson td,.lesson th{border:1px solid #ccc;padding:3px 8px}.missing{border:1px dashed #b00;color:#900;
padding:8px;margin:10px 0;font-size:14px}h2{font-size:17px;margin:0 0 8px}.figs{display:none}details summary{cursor:
pointer;color:#555;font-size:14px}table.idx td,table.idx th{border:1px solid #ddd;padding:4px 10px}
table.idx{border-collapse:collapse}</style>"""


def drawings(folder: pathlib.Path) -> dict[tuple[int, int], dict]:
    """(photo, figure number) -> the figure entry, the second look taking the place of the first when it ran."""
    rows = {}
    for name in ("redraw.jsonl", "polished.jsonl"):
        path = folder / name
        if path.exists():
            for line in path.read_text(encoding="utf-8").splitlines():
                row = json.loads(line)
                rows[row["photo"]] = row
    found = {(photo, f["number"]): f for photo, row in rows.items() for f in row["figures"]}
    filled = folder / "filled.jsonl"  # the fill pass: a figure drawn after all, or the figure as text
    if filled.exists():
        for line in filled.read_text(encoding="utf-8").splitlines():
            row = json.loads(line)
            for f in row["figures"]:
                key = (row["photo"], f["number"])
                entry = dict(found.get(key) or {"number": f["number"]})
                if f["kind"] == "figure" and (f.get("check") or {}).get("ok"):
                    entry.update(drawable=True, figure=f["figure"], check=f["check"])
                elif f["kind"] == "text" and f.get("text"):
                    entry["as_text"] = f["text"]
                found[key] = entry
    return found


def figure_html(entry: dict | None) -> str:
    if entry is None:
        return '<div class=missing>Figură încă nedesenată.</div>'
    if entry.get("drawable") and entry.get("figure") and (entry.get("check") or {}).get("ok", True):
        try:
            png = figures.render(entry["figure"])[0]
            return f'<img alt="{html.escape(entry.get("description") or "")}" src="data:image/png;base64,' \
                   f'{base64.b64encode(png).decode()}">'
        except Exception:  # noqa: BLE001 - shown as missing below
            pass
    return (f'<div class=missing>{html.escape(entry.get("description") or "Figură")} — nu se poate desena încă'
            f'{": " + html.escape(entry["missing"]) if entry.get("missing") else ""}.</div>')


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--redraw", default="redraw-all", help="the folder under data/theory with the drawings")
    args = parser.parse_args()
    settings = get_settings()
    drawn = drawings(THEORY / args.redraw)
    with psycopg.connect(settings.psycopg_dsn) as conn:
        rows = conn.execute(
            """SELECT ch.code, ch.title, ch.position, p.id, p.position, p.image_key, t.content
               FROM en_chapter_photos p JOIN en_chapters ch ON ch.id = p.chapter_id
               JOIN tutor_photo_transcripts t ON t.photo_id = p.id ORDER BY ch.position, p.position""").fetchall()
    out = THEORY / "lessons"
    out.mkdir(parents=True, exist_ok=True)
    chapters: dict[str, list] = collections.defaultdict(list)
    for row in rows:
        chapters[row[0]].append(row)
    index = []
    for code, pages in chapters.items():
        sections, counts = [], collections.Counter()
        for _, title, _, photo, position, key, content in pages:
            text = readable(re.sub(r"^---\n.*?\n---\n", "", content, flags=re.S).strip())
            numbers = [int(n) for n in MARKER.findall(text)]
            hidden = []
            for n in dict.fromkeys(numbers):
                entry = drawn.get((photo, n))
                counts["figuri"] += 1
                counts["ca text" if entry and entry.get("as_text") else
                       "desenate" if entry and entry.get("drawable") else "nedesenate"] += 1
                hidden.append(f'<div class=figs id="{photo}-{n}">{figure_html(entry)}</div>')

            def placed(match: re.Match) -> str:
                entry = drawn.get((photo, int(match.group(1))))
                if entry and entry.get("as_text"):  # a table or a calculation scheme: part of the text itself
                    return f"\n\n{readable(entry['as_text'])}\n\n"
                return f"\n\n@@FIG{match.group(1)}@@\n\n"

            text = MARKER.sub(placed, text)
            book = base64.b64encode(prepare((pathlib.Path(settings.en_chapter_images_dir) / key).read_bytes(),
                                            900, 70)[0]).decode()
            sections.append(
                f'<section><h2>{html.escape(code)} · poza {position}</h2><div class=row>'
                f'<div class=book><details><summary>Pagina din carte</summary>'
                f'<img src="data:image/jpeg;base64,{book}"></details></div>'
                f'<div class=lesson-wrap>{"".join(hidden)}'
                f'<textarea class=md hidden data-page="{photo}">{html.escape(text)}</textarea></div></div></section>')
        counts["pagini"] = len(pages)
        (out / f"lesson-{code}.html").write_text(
            f"<!doctype html><html lang=ro><head><meta charset=utf-8><title>Lecția {code}</title>{STYLE}</head><body>"
            f"<p><a href=index.html>← toate capitolele</a></p><h1>{html.escape(code)} · {html.escape(pages[0][1])}</h1>"
            f"{''.join(sections)}{PAGE_SCRIPT}</body></html>", encoding="utf-8")
        index.append(f'<tr><td><a href="lesson-{code}.html">{html.escape(code)} · {html.escape(pages[0][1])}</a></td>'
                     f'<td>{counts["pagini"]}</td><td>{counts["figuri"]}</td><td>{counts["desenate"]}</td>'
                     f'<td>{counts["ca text"]}</td><td>{counts["nedesenate"]}</td></tr>')
        print(code, dict(counts))
    (out / "index.html").write_text(
        f"<!doctype html><html lang=ro><head><meta charset=utf-8><title>Lecțiile, curate</title>{STYLE}</head><body>"
        f"<h1>Lecțiile: textul paginilor și desenele aplicației</h1><p>{len(rows)} pagini. Textul vine din "
        f"transcrierile făcute deja; în locul fiecărei figuri e desenul aplicației. Poza din carte se deschide sub "
        f"„Pagina din carte”.</p><table class=idx><tr><th>Capitol</th><th>pagini</th><th>figuri</th><th>desenate</th>"
        f"<th>scrise ca text</th><th>nedesenate</th></tr>{''.join(index)}</table></body></html>", encoding="utf-8")
    print("pagina:", out / "index.html")


if __name__ == "__main__":
    main()
