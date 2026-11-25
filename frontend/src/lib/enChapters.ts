import { type AnswerFeedback, type EnArea, type EnChapterLink, type EnChapterSummary, type ItemResultDto } from '@/lib/api'
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

/** The same for a student, who has no use for "0 poze": what there is to read, in words. */
export function enPageForStudents(chapter: Pick<EnChapterSummary, 'photoCount' | 'exampleCount'>): string {
  return [
    chapter.photoCount > 0 ? roCount(chapter.photoCount, 'pagină de teorie', 'pagini de teorie') : null,
    chapter.exampleCount > 0 ? roCount(chapter.exampleCount, 'model rezolvat', 'modele rezolvate') : null,
  ].filter(Boolean).join(' · ')
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

/**
 * The chapter after this one, in the order the list shows them (algebra, then geometry), that has something to read;
 * null at the end. It is where a student who finished a chapter goes on to.
 */
export function nextEnChapter(chapters: EnChapterSummary[], currentId: number): EnChapterSummary | null {
  const ordered = EN_AREAS.flatMap((area) => inArea(rootsOf(chapters), area.value))
  const at = ordered.findIndex((c) => c.id === currentId)
  if (at === -1) return null
  return ordered.slice(at + 1).find(hasEnContent) ?? null
}

/** The chapter before this one, in the same order as {@link nextEnChapter}, that has something to read; null at the start. */
export function previousEnChapter(chapters: EnChapterSummary[], currentId: number): EnChapterSummary | null {
  const ordered = EN_AREAS.flatMap((area) => inArea(rootsOf(chapters), area.value))
  const at = ordered.findIndex((c) => c.id === currentId)
  if (at === -1) return null
  return ordered.slice(0, at).reverse().find(hasEnContent) ?? null
}

/**
 * The test (or review) a student left to read a lesson travels with them as `?test=/quizzes/7/take`, through the next
 * and previous chapters and the chapter's practice, so "Înapoi la test" is there however far they went.
 */
export const RETURN_PARAM = 'test'

/** The page to go back to, from the address; only a path inside the app counts (never another site). */
export function returnPathOf(search: URLSearchParams): string | null {
  const path = search.get(RETURN_PARAM)
  if (!path || !path.startsWith('/') || path.startsWith('//') || path.includes('\\')) return null
  return path
}

/** Marks a lesson opened in a tab of its own, so "Înapoi la test" closes that tab instead of opening the test twice. */
const NEW_TAB_PARAM = 'fila'

/** `path` with the way back added, when there is one; `newTab` when the link opens a tab of its own. */
export function withReturn(path: string, back: string | null, newTab = false): string {
  if (!back) return path
  const query = `${RETURN_PARAM}=${encodeURIComponent(back)}${newTab ? `&${NEW_TAB_PARAM}=noua` : ''}`
  return `${path}${path.includes('?') ? '&' : '?'}${query}`
}

const LESSON_TAB_KEY = 'mathlms.lessonTab'

/**
 * Called when a lesson page opens: a lesson opened in a tab of its own (from a practice, so the test stays as it was)
 * says so in its address and starts that tab's history. The mark is per tab (sessionStorage), so it survives going on
 * to other chapters, whose links no longer carry it.
 */
export function rememberLessonTab(search: URLSearchParams): void {
  try {
    if (search.get(NEW_TAB_PARAM) === 'noua' && window.history.length === 1) sessionStorage.setItem(LESSON_TAB_KEY, '1')
  } catch {
    // Storage blocked: "Înapoi la test" then opens the test in this tab instead of closing it.
  }
}

/**
 * Back to the test: a tab opened only for the lesson closes, and the student is again on the test they left, answers
 * and all; otherwise (or when the browser does not let the tab close) the test opens here.
 */
export function backToTest(back: string, navigate: (to: string) => void): void {
  let ownTab = false
  try {
    ownTab = sessionStorage.getItem(LESSON_TAB_KEY) === '1'
  } catch {
    ownTab = false
  }
  if (ownTab) window.close()
  navigate(back)
}

/** What the way-back button says: the review page is not a test. */
export function returnLabel(back: string): string {
  return back.startsWith('/review') ? 'Înapoi la recapitulare' : 'Înapoi la test'
}

const comparable = (text: string) => text.toLocaleLowerCase('ro').replace(/\s+/g, ' ').trim().replace(/[.:]$/, '')

/**
 * A chapter's written text starts by repeating the chapter's name, which the page already has as its title: the
 * first line goes when it is only that, so the text starts with what it teaches.
 */
export function withoutRepeatedTitle(notes: string, title: string): string {
  const lines = notes.split('\n')
  const first = lines.findIndex((line) => line.trim() !== '')
  if (first === -1 || comparable(lines[first]) !== comparable(title)) return notes
  return lines.slice(first + 1).join('\n').replace(/^(\s*\n)+/, '')
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

/** One lesson to revise on a result: the chapter, and which requirements it is for ([] = the whole question). */
export interface RevisionLink {
  chapter: EnChapterLink
  parts: string[]
}

/** "Dacă vrei să recapitulezi noțiunile despre simetrie și segmente:", said about the requirements it is for, if any. */
export function revisionSentence(chapter: EnChapterLink, parts: string[] = []): string {
  const what = chapter.revisionTopic ? `noțiunile despre ${chapter.revisionTopic}` : 'noțiunile din acest capitol'
  const wish = `dacă vrei să recapitulezi ${what}:`
  return parts.length > 0
    ? `La ${parts.map((p) => `${p})`).join(', ')}, ${wish}`
    : wish.charAt(0).toUpperCase() + wish.slice(1)
}

/**
 * Where to revise after this question: each chapter (with something on its page) of a requirement that lost points,
 * or of the whole question when it has no requirements. Nothing while the teacher has not graded it yet - for a
 * problem with requirements, not before every one has its points (the grading autosave shows them one by one) - and
 * nothing for a question answered right.
 */
export function revisionLinks(item: ItemResultDto): RevisionLink[] {
  const parts = item.parts ?? []
  if (parts.length > 0) {
    if (item.awardedPoints === null) return [] // still being graded
    return byChapter(parts
      .filter((p) => p.awardedPoints !== null && p.awardedPoints < p.points)
      .map((p) => ({ chapter: p.enChapter, part: p.label })))
  }
  const lost = item.type !== 'OPEN'
    ? item.correct !== true
    : item.awardedPoints !== null && item.awardedPoints < item.points
  return lost ? byChapter([{ chapter: item.enChapter, part: null }]) : []
}

/** Every EN chapter (with a page) where a graded test lost points, once each, in paper order: "Ai greșit la: …". */
export function missedChapters(items: ItemResultDto[]): EnChapterLink[] {
  const seen = new Map<number, EnChapterLink>()
  for (const item of items) {
    for (const { chapter } of revisionLinks(item)) if (!seen.has(chapter.id)) seen.set(chapter.id, chapter)
  }
  return [...seen.values()]
}

/**
 * The lessons beside an open problem's barem in a practice: each requirement's chapter, or the problem's own when it
 * has none. No verdict needed - the student compares their work with the barem and decides.
 */
export function baremLessons(feedback: AnswerFeedback): RevisionLink[] {
  const parts = feedback.partLessons ?? []
  return parts.length > 0
    ? byChapter(parts.map((p) => ({ chapter: p.enChapter, part: p.label })))
    : byChapter([{ chapter: feedback.enChapter, part: null }])
}

/** One link per chapter with something on its page; requirements sharing a chapter share its sentence. */
function byChapter(entries: { chapter: EnChapterLink | null | undefined; part: string | null }[]): RevisionLink[] {
  const links: RevisionLink[] = []
  for (const { chapter, part } of entries) {
    if (!chapter?.ready) continue
    const existing = links.find((l) => l.chapter.id === chapter.id)
    if (existing) {
      if (part) existing.parts.push(part)
    } else {
      links.push({ chapter, parts: part ? [part] : [] })
    }
  }
  return links
}
