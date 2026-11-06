import { useMemo } from 'react'
import { MathContent } from '@/components/MathContent'
import { PhotoCrop } from '@/components/PhotoCrop'
import { cn } from '@/lib/utils'

/** A run of inline text: plain or bold. Math ($...$) stays inside the text and is drawn by MathContent. */
export interface InlineRun {
  text: string
  bold: boolean
}

export type Block =
  | { kind: 'paragraph'; text: string }
  | { kind: 'heading'; text: string }
  | { kind: 'list'; items: string[] }
  | { kind: 'table'; header: string[]; rows: string[][] }
  /** A drawing: the part (x, y, w, h as fractions) of the page photo `photoId`. */
  | { kind: 'figure'; alt: string; photoId: number; x: number; y: number; w: number; h: number }

// ![Axa numerelor](poza:42?x=0.078&y=0.396&w=0.844&h=0.061)
const FIGURE = /^\s*!\[([^\]]*)\]\(poza:(\d+)\?x=([\d.]+)&y=([\d.]+)&w=([\d.]+)&h=([\d.]+)\)\s*$/

const MATH = /\$\$[\s\S]+?\$\$|\$[^$\n]+?\$/g
const ITALIC = /(?<![*\w])\*(?![*\s])([^*\n]+?)(?<![*\s])\*(?![*\w])/g
const PLACEHOLDER = /\u0000(\d+)\u0000/g

/**
 * Bold runs of one piece of text. Math is set aside first so its asterisks (the star of N*) and underscores are never
 * read as emphasis; a lone *italic* marker is dropped and its words kept.
 */
export function inlineRuns(source: string): InlineRun[] {
  const maths: string[] = []
  const hidden = source.replace(MATH, (m) => {
    maths.push(m)
    return `\u0000${maths.length - 1}\u0000`
  })
  const plain = hidden.replace(ITALIC, '$1')
  const runs: InlineRun[] = []
  plain.split('**').forEach((part, i) => {
    if (part !== '') runs.push({ text: part.replace(PLACEHOLDER, (_, n) => maths[Number(n)]), bold: i % 2 === 1 })
  })
  return runs
}

/** "• text" or "- text": the chapter texts write their points as bullets. */
const BULLET = /^\s*(?:•|-)\s+(.*\S)\s*$/

/**
 * The line right above bullets names what they are about ("Radicali") when it is short and does not end like a
 * sentence or a lead-in ("Reținem:"): then it is a heading, not a paragraph.
 */
const namesTheBullets = (line: string) => {
  const text = line.trim()
  return text.length > 0 && text.length <= 80 && !/[.!?;:,]$/.test(text) && !text.includes('$')
}

const isTableLine = (line: string) => line.trim().startsWith('|')
const isSeparator = (line: string) => /^\s*\|?[\s:|-]*-{3,}[\s:|-]*\|?\s*$/.test(line)

function cells(line: string): string[] {
  return line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((c) => c.trim())
}

/**
 * The small part of Markdown the transcribed theory uses: paragraphs, headings, bullet lists, bold and pipe tables.
 * The chapter texts name each group of bullets on the line above them, with no "#": that line becomes a heading.
 */
export function parseBlocks(markdown: string): Block[] {
  const blocks: Block[] = []
  let paragraph: string[] = []
  let list: string[] = []
  let table: string[] = []

  const flushParagraph = () => {
    if (paragraph.length > 0) blocks.push({ kind: 'paragraph', text: paragraph.join('\n') })
    paragraph = []
  }
  const flushList = () => {
    if (list.length > 0) blocks.push({ kind: 'list', items: list })
    list = []
  }
  const flushTable = () => {
    const rows = table.filter((line) => !isSeparator(line)).map(cells)
    if (rows.length > 0) blocks.push({ kind: 'table', header: rows[0], rows: rows.slice(1) })
    table = []
  }

  for (const line of markdown.split('\n')) {
    if (isTableLine(line)) {
      flushParagraph()
      flushList()
      table.push(line)
      continue
    }
    flushTable()
    const bullet = BULLET.exec(line)
    if (bullet) {
      if (paragraph.length === 1 && namesTheBullets(paragraph[0])) {
        blocks.push({ kind: 'heading', text: paragraph[0].trim() })
        paragraph = []
      }
      flushParagraph()
      list.push(bullet[1])
      continue
    }
    flushList()
    const figure = FIGURE.exec(line)
    if (figure) {
      flushParagraph()
      blocks.push({
        kind: 'figure',
        alt: figure[1],
        photoId: Number(figure[2]),
        x: Number(figure[3]),
        y: Number(figure[4]),
        w: Number(figure[5]),
        h: Number(figure[6]),
      })
      continue
    }
    const heading = /^#{1,6}\s+(.*\S)\s*$/.exec(line)
    if (heading) {
      flushParagraph()
      blocks.push({ kind: 'heading', text: heading[1] })
    } else if (line.trim() === '') {
      flushParagraph()
    } else {
      paragraph.push(line)
    }
  }
  flushParagraph()
  flushList()
  flushTable()
  return blocks
}

function Inline({ text }: { text: string }) {
  return (
    <>
      {inlineRuns(text).map((run, i) =>
        run.bold ? (
          <strong key={i}><MathContent inline>{run.text}</MathContent></strong>
        ) : (
          <MathContent key={i} inline>{run.text}</MathContent>
        ),
      )}
    </>
  )
}

/**
 * Theory written as text in the app: paragraphs and bold labels, tables as tables and formulas typeset, all as React
 * elements (the text is never inserted as HTML, so nothing in it can inject markup).
 */
export function MarkdownText({ children, className, photoUrl, headingLevel = 3 }: {
  children: string
  className?: string
  /** Where a page photo is served; without it a figure shows only its description. */
  photoUrl?: (photoId: number) => string
  /** The level of the text's own headings, one below the heading the text sits under. */
  headingLevel?: 2 | 3 | 4 | 5 | 6
}) {
  const blocks = useMemo(() => parseBlocks(children), [children])
  const Heading = `h${headingLevel}` as const
  return (
    <div className={cn('space-y-3', className)}>
      {blocks.map((block, i) => {
        if (block.kind === 'figure') {
          return photoUrl ? (
            <figure key={i} className="my-2">
              <PhotoCrop src={photoUrl(block.photoId)} box={block} alt={block.alt} />
            </figure>
          ) : (
            <p key={i} className="text-sm italic text-muted-foreground">[figură: {block.alt}]</p>
          )
        }
        if (block.kind === 'heading') {
          return <Heading key={i} className="pt-1 text-base font-semibold"><Inline text={block.text} /></Heading>
        }
        if (block.kind === 'list') {
          return (
            <ul key={i} className="list-disc space-y-1 pl-5 marker:text-muted-foreground">
              {block.items.map((item, j) => (
                <li key={j}><Inline text={item} /></li>
              ))}
            </ul>
          )
        }
        if (block.kind === 'table') {
          return (
            <div key={i} className="overflow-x-auto">
              <table className="min-w-full border-collapse text-sm">
                <thead>
                  <tr>
                    {block.header.map((cell, c) => (
                      <th key={c} className="border bg-muted px-2 py-1 text-left font-medium"><Inline text={cell} /></th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {block.rows.map((row, r) => (
                    <tr key={r}>
                      {row.map((cell, c) => (
                        <td key={c} className="border px-2 py-1"><Inline text={cell} /></td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
        }
        return <p key={i} style={{ whiteSpace: 'pre-wrap' }}><Inline text={block.text} /></p>
      })}
    </div>
  )
}
