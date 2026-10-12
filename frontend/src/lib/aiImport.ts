import type { ExtractedItem, ItemInput, QuizItemType } from '@/lib/api'

/** Points a question gets when none are printed next to it (EN: a grilă is 5 p, a full solution usually 10 p). */
export const DEFAULT_POINTS: Record<QuizItemType, number> = { SINGLE_CHOICE: 5, OPEN: 10 }

/** A question in the review: what the AI read, as the teacher edits it. */
export interface DraftItem {
  key: number
  include: boolean
  page: number
  section: string
  type: QuizItemType
  statement: string
  options: string[]
  /** Index into `options` of the right answer; null until the teacher marks it (the AI never does). */
  correct: number | null
  points: number
  /** The barem; optional. */
  solution: string
  /** Has a drawing: the question waits for its picture in the editor. */
  imageRequired: boolean
  warnings: string[]
  /** An open problem's requirements a), b)... (EN Subiectul III); the question's points are then their sum. */
  parts: DraftPart[]
}

export interface DraftPart {
  statement: string
  points: number
}

/** A question's points: its own, or the sum of its requirements. */
export const draftPoints = (d: DraftItem) => (d.parts.length > 0 ? d.parts.reduce((s, p) => s + p.points, 0) : d.points)

export function toDrafts(items: ExtractedItem[]): DraftItem[] {
  return items.map((item, index) => ({
    key: index,
    include: true,
    page: item.page,
    section: item.section ?? '',
    type: item.type,
    statement: item.statement,
    options: item.type === 'SINGLE_CHOICE' ? [...item.options] : [],
    correct: null,
    points: item.points ?? DEFAULT_POINTS[item.type],
    solution: '',
    imageRequired: item.hasFigure,
    warnings: item.warnings,
    parts: item.type === 'OPEN'
      ? (item.parts ?? []).map((p) => ({ statement: p.statement, points: p.points ?? 0 }))
      : [],
  }))
}

/** What still stops the quiz from being made, one line per question ("Subiectul 2: marchează răspunsul corect."). */
export function draftProblems(drafts: DraftItem[]): string[] {
  const problems: string[] = []
  drafts.filter((d) => d.include).forEach((d, index) => {
    const label = `Subiectul ${index + 1}`
    if (d.statement.trim() === '') {
      problems.push(`${label}: enunțul e gol.`)
    }
    if (!Number.isInteger(draftPoints(d)) || draftPoints(d) < 0 || draftPoints(d) > 100) {
      problems.push(`${label}: punctajul trebuie să fie între 0 și 100.`)
    }
    if (d.type === 'OPEN' && d.parts.some((p) => p.statement.trim() === '')) {
      problems.push(`${label}: o cerință nu are enunț.`)
    }
    if (d.type === 'SINGLE_CHOICE') {
      const filled = d.options.filter((o) => o.trim() !== '').length
      if (filled < 2) {
        problems.push(`${label}: o grilă are nevoie de cel puțin 2 variante.`)
      } else if (d.correct === null || (d.options[d.correct] ?? '').trim() === '') {
        problems.push(`${label}: marchează varianta corectă.`)
      }
    }
  })
  return problems
}

/** The kept questions, as the quiz builder takes them (empty options dropped, the marked one correct). */
export function toItemInputs(drafts: DraftItem[]): ItemInput[] {
  return drafts.filter((d) => d.include).map((d, index) => {
    const options = d.type === 'SINGLE_CHOICE'
      ? d.options
          .map((text, i) => ({ text: text.trim(), correct: i === d.correct }))
          .filter((o) => o.text !== '')
          .map((o, position) => ({ position, text: o.text, correct: o.correct }))
      : null
    return {
      type: d.type,
      position: index,
      statement: d.statement.trim(),
      points: draftPoints(d),
      solution: d.solution.trim() === '' ? null : d.solution.trim(),
      options,
      hints: [],
      section: d.section.trim() === '' ? null : d.section.trim(),
      imageRequired: d.imageRequired,
      parts: d.type === 'OPEN' && d.parts.length > 0
        ? d.parts.map((p) => ({ statement: p.statement.trim(), points: p.points, solution: null }))
        : null,
    }
  })
}
