import { type EnArea, type EnChapterSummary } from '@/lib/api'
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
