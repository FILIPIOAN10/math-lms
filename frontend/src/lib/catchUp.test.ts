import { describe, expect, it } from 'vitest'
import { catchUpQuizzes, defaultCatchUpDue, weakStudentIds } from '@/lib/catchUp'
import type { ClassMastery, QuizSummary } from '@/lib/api'

const grid: ClassMastery = {
  chapters: [
    { id: 30, title: 'Fracții', bookTitle: 'Algebra' },
    { id: 31, title: 'Ecuații', bookTitle: 'Algebra' },
  ],
  students: [
    { studentId: 1, fullName: 'Ana Pop', cells: [{ chapterId: 30, percent: 35, questions: 4 }, { chapterId: 31, percent: 90, questions: 2 }] },
    { studentId: 2, fullName: 'Bob Ionescu', cells: [{ chapterId: 30, percent: 65, questions: 3 }, { chapterId: 31, percent: 20, questions: 2 }] },
    { studentId: 3, fullName: 'Cris Rus', cells: [{ chapterId: 30, percent: null, questions: 0 }, { chapterId: 31, percent: 49, questions: 1 }] },
  ],
}

const quiz = (id: number, over: Partial<QuizSummary>): QuizSummary => ({
  id, title: `Test ${id}`, description: null, status: 'PUBLISHED', schoolClassId: null, schoolClassName: null,
  timeLimitMinutes: null, practiceAllowed: false, chapterId: null, maxAttempts: null, ...over,
} as QuizSummary)

describe('weakStudentIds', () => {
  it('picks the students below 50% on the chapter, not the ones without data', () => {
    expect(weakStudentIds(grid, 0)).toEqual([1])
    expect(weakStudentIds(grid, 1)).toEqual([2, 3])
  })
})

describe('catchUpQuizzes', () => {
  it("keeps the published quizzes this class may get, the chapter's own first", () => {
    const quizzes = [
      quiz(1, { title: 'Recapitulare' }),
      quiz(2, { title: 'Fracții — exersare', chapterId: 30 }),
      quiz(3, { title: 'Ciornă', chapterId: 30, status: 'DRAFT' }),
      quiz(4, { title: 'Altă clasă', chapterId: 30, schoolClassId: 9 }),
      quiz(5, { title: 'Doar clasa noastră', schoolClassId: 5 }),
    ]

    const { chapter, other } = catchUpQuizzes(quizzes, 30, 5)

    expect(chapter.map((q) => q.title)).toEqual(['Fracții — exersare'])
    expect(other.map((q) => q.title)).toEqual(['Recapitulare', 'Doar clasa noastră'])
  })
})

describe('defaultCatchUpDue', () => {
  it('is one week later at 20:00 local time', () => {
    expect(defaultCatchUpDue(new Date(2026, 10, 9, 15, 30))).toBe('2026-11-16T20:00')
  })
})
