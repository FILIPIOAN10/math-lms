import type { ClassMastery, QuizSummary } from '@/lib/api'
import { masteryLevel } from '@/lib/mastery'

/**
 * The students to give catch-up homework on one chapter (a column of the mastery grid): those "De recapitulat" there
 * (below 50%). A student with no scored answer in it is not picked - there is nothing saying they are weak.
 */
export function weakStudentIds(grid: ClassMastery, column: number): number[] {
  return grid.students
    .filter((s) => masteryLevel(s.cells[column]?.percent ?? null) === 'low')
    .map((s) => s.studentId)
}

/**
 * The quizzes a catch-up homework can use for one class: published, and either for every class or reserved for this one.
 * The ones tagged with the chapter come first (they are what the weak students should practise), the rest after.
 */
export function catchUpQuizzes(
  quizzes: QuizSummary[],
  chapterId: number,
  classId: number,
): { chapter: QuizSummary[]; other: QuizSummary[] } {
  const usable = quizzes.filter((q) => q.status === 'PUBLISHED' && (q.schoolClassId === null || q.schoolClassId === classId))
  return {
    chapter: usable.filter((q) => q.chapterId === chapterId),
    other: usable.filter((q) => q.chapterId !== chapterId),
  }
}

/** The deadline the dialog starts with: a week from now, 20:00, as a datetime-local value. */
export function defaultCatchUpDue(now: Date = new Date()): string {
  const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 7, 20, 0)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
