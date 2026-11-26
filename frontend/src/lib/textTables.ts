/**
 * Tables inside a question's text. KaTeX typesets formulas but not tables, so a statement with a table showed its raw
 * source ("\begin{tabular}{|l|c|} \hline volei & baschet \\ ..."). This finds the two ways a table gets into a
 * statement and hands it back as rows of cells, for MathContent to draw as a real table:
 * - LaTeX `\begin{tabular}{...} ... \end{tabular}` (what the AI import wrote): rows end in `\\`, cells split on `&`,
 *   `\hline` and the column spec are dropped;
 * - a Markdown table (`| a | b |` lines with a `|---|` line under the first): the first row is the header.
 * Cells stay text: formulas in them ($x^2$) are typeset like anywhere else.
 */
export type TextBlock =
  | { kind: 'text'; text: string }
  | { kind: 'table'; header: boolean; rows: string[][] }

const TABULAR = /\\begin\{tabular\}\s*(?:\{[^}]*\})?([\s\S]*?)\\end\{tabular\}/g
/** Rule commands that only draw lines. */
const RULES = /\\(?:hline|toprule|midrule|bottomrule|cline\{[^}]*\})/g

export function splitTables(text: string): TextBlock[] {
  const blocks: TextBlock[] = []
  let last = 0
  for (const match of text.matchAll(TABULAR)) {
    pushText(blocks, text.slice(last, match.index))
    const rows = tabularRows(match[1])
    if (rows.length > 0) blocks.push({ kind: 'table', header: false, rows })
    last = match.index + match[0].length
  }
  pushText(blocks, text.slice(last))
  return blocks.length > 0 ? blocks : [{ kind: 'text', text }]
}

function tabularRows(body: string): string[][] {
  return body
    .replace(RULES, '')
    .split(/\\\\/)
    .map((row) => row.split(/(?<!\\)&/).map((cell) => cell.replace(/\\&/g, '&').trim()))
    .filter((cells) => cells.some((cell) => cell !== ''))
}

/** Text between LaTeX tables, itself split around any Markdown table; blank edges trimmed, empty pieces dropped. */
function pushText(blocks: TextBlock[], text: string) {
  const lines = text.split('\n')
  let buffer: string[] = []
  const flush = () => {
    const joined = buffer.join('\n').replace(/^\s*\n|\n\s*$/g, '').trim()
    if (joined !== '') blocks.push({ kind: 'text', text: joined })
    buffer = []
  }
  for (let i = 0; i < lines.length; i++) {
    if (isRow(lines[i]) && i + 1 < lines.length && isSeparator(lines[i + 1])) {
      let end = i + 2
      while (end < lines.length && isRow(lines[end])) end++
      flush()
      const rows = [lines[i], ...lines.slice(i + 2, end)].map(cells)
      blocks.push({ kind: 'table', header: true, rows })
      i = end - 1
    } else {
      buffer.push(lines[i])
    }
  }
  flush()
}

function isRow(line: string): boolean {
  const t = line.trim()
  return t.startsWith('|') && t.endsWith('|') && t.length > 1
}

function isSeparator(line: string): boolean {
  return /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?\s*$/.test(line)
}

function cells(line: string): string[] {
  return line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((cell) => cell.trim())
}
