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
