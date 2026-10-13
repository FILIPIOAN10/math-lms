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
