/**
 * EN-style tests group their questions under headings ("SUBIECTUL I" + the instruction under it). A question's
 * `section` holds that heading; this file turns a list of questions into what the screens show: a heading where a
 * section starts, and numbers that start again at 1 under each heading. A test without headings keeps 1, 2, 3…
 */

export interface SectionHeading {
  /** The first line, e.g. "SUBIECTUL I". */
  title: string
  /** The lines under it (the instruction), or null. */
  note: string | null
  /** The points of the questions under it ("30 de puncte" on the EN paper); absent when unknown. */
  total?: number
}

export interface NumberedItem<T> {
  item: T
  /** The number inside its section: 1, 2, 3… again under each heading. */
  local: number
  /** The short label for tight spots (navigator, lists of numbers): "3", or "I.3" in a test with headings. */
  label: string
  /** Set on the first question of a section: the heading to show above it. */
  heading: SectionHeading | null
}

const ROMAN = /^subiectul\s+(?:al\s+)?([ivxlc]+)\b/i

export function parseSection(section: string): SectionHeading {
  const [first, ...rest] = section.split('\n')
  const note = rest.join('\n').trim()
  return { title: first.trim(), note: note === '' ? null : note }
}

export function numberItems<T extends { section?: string | null; points?: number }>(items: T[]): NumberedItem<T>[] {
  const sectioned = items.some((i) => i.section)
  // The points of each run of questions under the same heading, by the index of its first question.
  const totals = new Map<number, number>()
  let runStart = 0
  items.forEach((item, index) => {
    if (index > 0 && (item.section ?? null) !== (items[index - 1].section ?? null)) runStart = index
    totals.set(runStart, (totals.get(runStart) ?? 0) + (item.points ?? 0))
  })
  let group = 0
  let local = 0
  let previous: string | null | undefined
  return items.map((item, index) => {
    if (!sectioned) {
      return { item, local: index + 1, label: String(index + 1), heading: null }
    }
    const section = item.section ?? null
    const starts = index === 0 || section !== previous
    previous = section
    if (starts) {
      group++
      local = 0
    }
    local++
    if (section === null) {
      return { item, local, label: String(local), heading: null }
    }
    const heading = parseSection(section)
    const short = ROMAN.exec(heading.title)?.[1].toUpperCase() ?? String(group)
    const total = items.some((i) => i.points !== undefined) ? totals.get(index) : undefined
    return { item, local, label: `${short}.${local}`, heading: starts ? { ...heading, total } : null }
  })
}
