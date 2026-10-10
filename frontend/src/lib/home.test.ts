import { describe, expect, it } from 'vitest'
import { latestGrade, todoAssignments, upcomingDeadlines } from '@/lib/home'
import type { AssignmentSummary, ProgressPointDto, StudentAssignment } from '@/lib/api'

const DAY = 86_400_000
const NOW = Date.parse('2026-10-10T12:00:00Z')
const at = (days: number) => new Date(NOW + days * DAY).toISOString()

const homework = (id: number, state: StudentAssignment['state']): StudentAssignment => ({
  assignmentId: id, quizId: id, quizTitle: `Q${id}`, schoolClassName: 'a 9-a', dueAt: at(1), state,
  late: false, overdue: false, attemptId: null, timeLimitMinutes: null,
})

const assignment = (id: number, dueInDays: number, over: Partial<AssignmentSummary> = {}): AssignmentSummary => ({
  id, quizId: id, quizTitle: `Q${id}`, schoolClassId: 1, schoolClassName: 'a 9-a', dueAt: at(dueInDays),
  overdue: dueInDays < 0, enrolled: 20, done: 5, late: 0, inProgress: 0, notStarted: 15, ...over,
})

const point = (attemptId: number, submittedAt: string): ProgressPointDto => ({
  attemptId, quizId: 1, quizTitle: `T${attemptId}`, submittedAt, score: 8, maxScore: 10, percent: 80,
})

describe('todoAssignments', () => {
  it('keeps only homework that still has to be handed in, in the server order', () => {
    const list = [homework(1, 'IN_PROGRESS'), homework(2, 'SUBMITTED'), homework(3, 'NOT_STARTED'), homework(4, 'GRADED')]
    expect(todoAssignments(list).map((a) => a.assignmentId)).toEqual([1, 3])
  })
})

describe('latestGrade', () => {
  it('is the most recently handed-in graded test, whatever the list order', () => {
    const points = [point(1, '2026-10-01T10:00:00Z'), point(3, '2026-10-08T10:00:00Z'), point(2, '2026-10-05T10:00:00Z')]
    expect(latestGrade(points)?.attemptId).toBe(3)
  })

  it('is null before the first grade', () => {
    expect(latestGrade([])).toBeNull()
  })
})

describe('upcomingDeadlines', () => {
  it('lists open homework soonest first, with recently overdue homework that still misses students on top', () => {
    const list = [
      assignment(1, 5),
      assignment(2, -2), // overdue 2 days, 15 students missing -> needs attention
      assignment(3, 1),
      assignment(4, -3, { done: 20, notStarted: 0 }), // overdue but everyone handed in -> nothing to do
      assignment(5, -30), // overdue a month ago -> old news
    ]
    expect(upcomingDeadlines(list, NOW).map((a) => a.id)).toEqual([2, 3, 1])
  })

  it('shows at most `limit` rows', () => {
    const list = [1, 2, 3, 4, 5, 6, 7].map((d) => assignment(d, d))
    expect(upcomingDeadlines(list, NOW, 5)).toHaveLength(5)
  })
})
