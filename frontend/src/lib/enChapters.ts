import { type EnArea, type EnChapterLink, type EnChapterSummary, type ItemResultDto } from '@/lib/api'
import { roCount } from '@/lib/format'

/** The two parts of the list, in the order the pages show them. */
export const EN_AREAS: { value: EnArea; label: string }[] = [
  { value: 'ALGEBRA', label: 'Algebră' },
  { value: 'GEOMETRY', label: 'Geometrie' },
]

/** "2 poze · 3 modele": what is on a chapter's page. */
export function enPageContents(chapter: Pick<EnChapterSummary, 'photoCount' | 'exampleCount'>): string {
  return `${roCount(chapter.photoCount, 'poză', 'poze')} · ${roCount(chapter.exampleCount, 'model', 'modele')}`
}

/** Whether a student has anything to read on the page. */
export function hasEnContent(chapter: Pick<EnChapterSummary, 'photoCount' | 'exampleCount'>): boolean {
  return chapter.photoCount > 0 || chapter.exampleCount > 0
}

/** The chapters of one part, in list order. */
export function inArea(chapters: EnChapterSummary[], area: EnArea): EnChapterSummary[] {
  return chapters.filter((c) => c.area === area)
}

/** The chapters themselves: every page that is not a section or a point of another. */
export function rootsOf(chapters: EnChapterSummary[]): EnChapterSummary[] {
  const ids = new Set(chapters.map((c) => c.id))
  return chapters.filter((c) => c.parentId === null || !ids.has(c.parentId))
}

/** One line of the teacher's list: a page, how deep it sits, and where it stands among its siblings. */
export interface TreeRow {
  chapter: EnChapterSummary
  /** 0 = chapter, 1 = section, 2 = point. */
  depth: number
  first: boolean
  last: boolean
  /** Sections or points directly under it. */
  childCount: number
}

/**
 * One part of the list as rows in book order: each chapter, then - when it is in `open` - its sections, each followed
 * by its points. Siblings keep the list order, so the rows are what "move up / down" acts on.
 */
export function treeRows(chapters: EnChapterSummary[], area: EnArea, open: ReadonlySet<number>): TreeRow[] {
  const inPart = inArea(chapters, area)
  const ids = new Set(inPart.map((c) => c.id))
  const children = new Map<number | null, EnChapterSummary[]>()
  for (const c of inPart) {
    const parent = c.parentId !== null && ids.has(c.parentId) ? c.parentId : null
    children.set(parent, [...(children.get(parent) ?? []), c])
  }
  const rows: TreeRow[] = []
  const walk = (parent: number | null, depth: number) => {
    const siblings = children.get(parent) ?? []
    siblings.forEach((chapter, i) => {
      rows.push({
        chapter,
        depth,
        first: i === 0,
        last: i === siblings.length - 1,
        childCount: (children.get(chapter.id) ?? []).length,
      })
      if (depth > 0 || open.has(chapter.id)) walk(chapter.id, depth + 1)
    })
  }
  walk(null, 0)
  return rows
}

/**
 * Whether the material of a section is there: its page of the book is among the photos uploaded to its chapter
 * ('in-materia'), the chapter has not been uploaded that far ('missing'), or the section has no page number.
 */
export function sectionPageStatus(
  section: EnChapterSummary,
  chapters: EnChapterSummary[],
): 'no-page' | 'in-materia' | 'missing' {
  if (section.bookPage === null) return 'no-page'
  const byId = new Map(chapters.map((c) => [c.id, c]))
  let root = section
  for (let step = 0; step < 4 && root.parentId !== null && byId.has(root.parentId); step++) {
    root = byId.get(root.parentId)!
  }
  return photoIndexOf(section.bookPage, root.bookPage, root.photoCount) === null ? 'missing' : 'in-materia'
}

/** How many levels of pages are above this one: 0 for a chapter, 1 for a section, 2 for a point. */
function levelOf(chapter: EnChapterSummary, byId: Map<number, EnChapterSummary>): number {
  let level = 0
  for (let next = chapter.parentId; next !== null && byId.has(next) && level < 5; next = byId.get(next)!.parentId) level++
  return level
}

/**
 * The pages a given page may sit under: a chapter or a section of the same part - never a point (nothing goes deeper),
 * the page itself or anything already under it. Each comes with its level, for indenting the choices.
 */
export function parentChoices(
  chapters: EnChapterSummary[],
  area: EnArea,
  selfId: number | null,
): { chapter: EnChapterSummary; level: number }[] {
  const byId = new Map(chapters.map((c) => [c.id, c]))
  const underSelf = (c: EnChapterSummary): boolean => {
    for (let next: number | null = c.id, steps = 0; next !== null && byId.has(next) && steps < 5; steps++) {
      if (next === selfId) return true
      next = byId.get(next)!.parentId
    }
    return false
  }
  return chapters
    .filter((c) => c.area === area && !(selfId !== null && underSelf(c)))
    .map((chapter) => ({ chapter, level: levelOf(chapter, byId) }))
    .filter(({ level }) => level < 2)
}

/**
 * Which of a chapter's photos shows the page where a section starts: the photos are the book's pages in order,
 * starting at the chapter's first page. null = unknown, or that page is not uploaded.
 */
export function photoIndexOf(sectionPage: number | null, chapterFirstPage: number | null, photoCount: number): number | null {
  if (sectionPage === null || chapterFirstPage === null) return null
  const index = sectionPage - chapterFirstPage
  return index >= 0 && index < photoCount ? index : null
}

/** One "Vezi modele" button on a result: the chapter, and which requirements it is for ([] = the whole question). */
export interface RevisionLink {
  chapter: EnChapterLink
  parts: string[]
}

/**
 * Where to revise after this question: each chapter (with something on its page) of a requirement that lost points,
 * or of the whole question when it has no requirements. Nothing while the teacher has not graded it yet - for a
 * problem with requirements, not before every one has its points (the grading autosave shows them one by one) - and
 * nothing for a question answered right.
 */
export function revisionLinks(item: ItemResultDto): RevisionLink[] {
  const parts = item.parts ?? []
  const links: RevisionLink[] = []
  const add = (chapter: EnChapterLink | null | undefined, part: string | null) => {
    if (!chapter?.ready) return
    const existing = links.find((l) => l.chapter.id === chapter.id)
    if (existing) {
      if (part) existing.parts.push(part)
    } else {
      links.push({ chapter, parts: part ? [part] : [] })
    }
  }
  if (parts.length > 0) {
    if (item.awardedPoints === null) return links // still being graded
    parts.filter((p) => p.awardedPoints !== null && p.awardedPoints < p.points).forEach((p) => add(p.enChapter, p.label))
    return links
  }
  const lost = item.type === 'SINGLE_CHOICE'
    ? item.correct !== true
    : item.awardedPoints !== null && item.awardedPoints < item.points
  if (lost) add(item.enChapter, null)
  return links
}
