import { type AssignmentSummary, type ProgressPointDto, type StudentAssignment } from '@/lib/api'

const DAY = 86_400_000

/** Homework still to hand in. The server already orders it (not done first, soonest first), so the order is kept. */
export function todoAssignments(list: StudentAssignment[]): StudentAssignment[] {
  return list.filter((a) => a.state === 'NOT_STARTED' || a.state === 'IN_PROGRESS')
}

/** The most recently handed-in graded test, or null before the first grade. */
export function latestGrade(points: ProgressPointDto[]): ProgressPointDto | null {
  return points.reduce<ProgressPointDto | null>(
    (latest, p) => (latest === null || Date.parse(p.submittedAt) > Date.parse(latest.submittedAt) ? p : latest),
    null,
  )
}

/**
 * The deadlines a teacher should look at, soonest first: every open assignment, plus those overdue for at most
 * `recentDays` that some students still have not handed in. Older overdue ones are old news, and an overdue one
 * everybody handed in needs nothing.
 */
export function upcomingDeadlines(
  list: AssignmentSummary[],
  nowMs: number,
  limit = 5,
  recentDays = 7,
): AssignmentSummary[] {
  return list
    .filter((a) => {
      if (!a.overdue) return true
      return a.done < a.enrolled && nowMs - Date.parse(a.dueAt) <= recentDays * DAY
    })
    .sort((a, b) => Date.parse(a.dueAt) - Date.parse(b.dueAt))
    .slice(0, limit)
}

/** One step of a new teacher's first run (T7): what to do, where, and whether it is done already. */
export interface FirstRunStep {
  key: 'class' | 'students' | 'quiz' | 'homework'
  label: string
  hint: string
  to: string
  done: boolean
}

/**
 * The four steps from an empty app to the first homework, each ticked off by the data itself (nothing is stored):
 * a class exists, a student has joined, a quiz exists, a homework was given.
 */
export function firstRunSteps(counts: { classes: number; students: number; quizzes: number; assignments: number }): FirstRunStep[] {
  return [
    { key: 'class', label: 'Creează o clasă', hint: 'Clase → Adaugă clasă', to: '/admin/content', done: counts.classes > 0 },
    {
      key: 'students', label: 'Invită elevii', hint: 'Clase → Invită elevi pe rândul clasei, apoi trimite-le linkul',
      to: '/admin/content', done: counts.students > 0,
    },
    {
      key: 'quiz', label: 'Creează un test', hint: 'Teste → Adaugă quiz (sau, dintr-un capitol, Creează quiz din capitol)',
      to: '/admin/quizzes', done: counts.quizzes > 0,
    },
    {
      key: 'homework', label: 'Dă-l ca temă', hint: 'Teste → Publică, alege clasa și termenul',
      to: '/admin/quizzes', done: counts.assignments > 0,
    },
  ]
}
