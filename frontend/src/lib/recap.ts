import type { EnClassMastery, RecapExercise, RecapReview } from '@/lib/api'
import { masteryLevel } from '@/lib/mastery'
import { searchKey } from '@/lib/search'

/** The collection list's filters; '' = any. */
export interface RecapListFilter {
  review: RecapReview | ''
  /** An EN chapter id, as a string. */
  chapter: string
  /** 'agree' = both AI runs agreed, 'check' = everything else. */
  ai: 'agree' | 'check' | ''
  search: string
}

export const REVIEW_LABELS: Record<RecapReview, string> = {
  DRAFT: 'De verificat',
  APPROVED: 'Aprobat',
  REJECTED: 'Respins',
}

export function recapMatches(exercise: RecapExercise, filter: RecapListFilter): boolean {
  if (filter.review && exercise.review !== filter.review) return false
  if (filter.chapter && String(exercise.enChapterId) !== filter.chapter) return false
  if (filter.ai === 'agree' && exercise.aiStatus !== 'AGREE') return false
  if (filter.ai === 'check' && exercise.aiStatus === 'AGREE') return false
  const words = searchKey(filter.search.trim())
  if (!words) return true
  const text = searchKey([exercise.key, exercise.statement, ...exercise.parts.map((p) => p.statement)].join(' '))
  return text.includes(words)
}

const STATUS: Record<string, { label: string; tone: 'success' | 'warning' }> = {
  AGREE: { label: 'Verificat de AI', tone: 'success' },
  PARTIAL: { label: 'Parțial verificat', tone: 'warning' },
  PROOF: { label: 'Demonstrație: verifică tu', tone: 'warning' },
  NEEDS_FIGURE: { label: 'AI-ul a cerut figura', tone: 'warning' },
  UNPARSED: { label: 'Răspuns necomparat', tone: 'warning' },
}

/** What the two AI runs said, in words: only AGREE is "checked"; everything else needs a careful read. */
export function recapStatusLabel(exercise: RecapExercise): { label: string; tone: 'success' | 'warning' } {
  return STATUS[exercise.aiStatus] ?? { label: 'De verificat', tone: 'warning' }
}

/** The students to give catch-up homework on one EN chapter (a column of the EN grid): those below 50% there. */
export function weakEnStudentIds(grid: EnClassMastery, column: number): number[] {
  return grid.students
    .filter((s) => masteryLevel(s.cells[column]?.percent ?? null) === 'low')
    .map((s) => s.studentId)
}
