import type { PhotoBox } from '@/components/PhotoCrop'
import type { EnChapterSummary, ExtractedItem, FigureInput, ItemInput, QuizItemType } from '@/lib/api'

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
  /** The EN chapter's code ("A1"), proposed by the AI and changeable; '' = automatic (by its place on the paper). */
  enChapter: string
  /** Why the AI proposed it; null when it did not. */
  enChapterReason: string | null
  /** The drawing cut out of a page photo, to become the question's picture; null = none (added by hand, if at all). */
  figure: DraftFigure | null
}

/** A drawing's place: page `page` (1-based), `box` = [ymin, xmin, ymax, xmax] on a 0-1000 scale (as the AI gives it). */
export interface DraftFigure {
  page: number
  box: number[]
}

export interface DraftPart {
  statement: string
  points: number
  /** '' = automatic */
  enChapter: string
  enChapterReason: string | null
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
      ? (item.parts ?? []).map((p) => ({
          statement: p.statement, points: p.points ?? 0, enChapter: p.enChapter ?? '', enChapterReason: p.enChapterReason ?? null,
        }))
      : [],
    enChapter: item.enChapter ?? '',
    enChapterReason: item.enChapterReason ?? null,
    figure: item.figurePage && item.figureBox?.length === 4 ? { page: item.figurePage, box: [...item.figureBox] } : null,
  }))
}

/** [ymin, xmin, ymax, xmax] on 0-1000 -> the fractions of the photo the picture is drawn from. */
export function boxToPhoto(box: number[]): PhotoBox {
  const [ymin, xmin, ymax, xmax] = box
  return { x: xmin / 1000, y: ymin / 1000, w: (xmax - xmin) / 1000, h: (ymax - ymin) / 1000 }
}

const onPage = (value: number) => Math.min(1000, Math.max(0, Math.round(value * 1000)))

/** Fractions of the photo -> [ymin, xmin, ymax, xmax] on 0-1000, kept on the page. */
export function photoToBox(box: PhotoBox): number[] {
  return [onPage(box.y), onPage(box.x), onPage(box.y + box.h), onPage(box.x + box.w)]
}

/** Smaller than this (1.5 % of the photo, either way) is a slip of the finger, not a drawing. */
const MIN_FIGURE = 0.015

/** The rectangle between where a drag started and ended (either direction), as fractions; null for a mere click. */
export function boxFromDrag(start: { x: number; y: number }, end: { x: number; y: number }): PhotoBox | null {
  const clamp = (v: number) => Math.min(1, Math.max(0, v))
  const x = clamp(Math.min(start.x, end.x))
  const y = clamp(Math.min(start.y, end.y))
  const w = clamp(Math.max(start.x, end.x)) - x
  const h = clamp(Math.max(start.y, end.y)) - y
  return w < MIN_FIGURE || h < MIN_FIGURE ? null : { x, y, w, h }
}

/** The drawings to cut out, for the kept questions only, each numbered by its place among them (as toItemInputs). */
export function toFigureInputs(drafts: DraftItem[]): FigureInput[] {
  return drafts.filter((d) => d.include).flatMap((d, index) =>
    d.figure ? [{ index, page: d.figure.page, box: [...d.figure.box] }] : [])
}

/** What still stops the quiz from being made, one line per question ("Subiectul 2: marchează răspunsul corect."). */
export function draftProblems(drafts: DraftItem[]): string[] {
  const problems: string[] = []
  drafts.filter((d) => d.include).forEach((d, index) => {
    const label = `Întrebarea ${index + 1}`
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
export function toItemInputs(drafts: DraftItem[], enChapters: EnChapterSummary[] = []): ItemInput[] {
  // A code the list no longer has (renamed meanwhile) falls back to automatic.
  const idOf = (code: string) => (code === '' ? null : enChapters.find((c) => c.code === code)?.id ?? null)
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
        ? d.parts.map((p) => ({ statement: p.statement.trim(), points: p.points, solution: null, enChapterId: idOf(p.enChapter) }))
        : null,
      enChapterId: idOf(d.enChapter),
    }
  })
}
