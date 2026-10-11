import { useId, useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { assignmentBadge, dueCountdown, type BadgeTone } from '@/lib/assignments'
import { formatDate } from '@/lib/format'
import { type StudentAssignment } from '@/lib/api'

const TONES: Record<BadgeTone, string> = {
  info: 'bg-sky-500/15 text-sky-700 dark:text-sky-300',
  success: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300',
  warning: 'bg-amber-500/15 text-amber-700 dark:text-amber-300',
  danger: 'bg-destructive/15 text-destructive',
}

/**
 * The student's homework: quizzes their teacher assigned to their class with a deadline. The deadline is soft - an
 * overdue assignment can still be done, it is only flagged. The list arrives ordered (not done first, soonest first).
 *
 * With `childId` it is the parent's read-only view of that child's homework: no buttons to start or continue, and a
 * handed-in one links to the parent's page for the result. `resultHref` makes it read-only the same way for the
 * teacher's page of one student, with its own link to the result. `emptyText` shows a line instead of hiding an empty
 * list.
 */
export function AssignmentsSection({
  assignments,
  nowMs,
  title = 'Teme',
  childId,
  resultHref,
  emptyText,
}: {
  assignments: StudentAssignment[]
  nowMs?: number
  title?: string
  childId?: number
  resultHref?: (attemptId: number) => string
  emptyText?: string
}) {
  const [loadedAt] = useState(() => Date.now()) // "now" is fixed when the list is shown; a test can pass its own
  const headingId = useId()
  const now = nowMs ?? loadedAt
  if (assignments.length === 0 && !emptyText) return null
  const readOnly = childId !== undefined || resultHref !== undefined
  const linkToResult = (attemptId: number) =>
    resultHref ? resultHref(attemptId)
      : childId !== undefined ? `/parent/children/${childId}/attempts/${attemptId}`
        : `/quizzes/attempts/${attemptId}/result`

  return (
    <section className="space-y-2" data-testid="assignments" aria-labelledby={headingId}>
      <h2 id={headingId} className="font-medium">{title}</h2>
      {assignments.length === 0 && <p className="text-sm text-muted-foreground">{emptyText}</p>}
      {assignments.map((a) => {
        const badge = assignmentBadge(a)
        const done = a.state === 'SUBMITTED' || a.state === 'GRADED'
        return (
          <Card key={a.assignmentId}>
            <CardContent data-testid="assignment-card" className="flex items-center justify-between gap-3 py-3">
              <div className="min-w-0 space-y-1">
                <p className="truncate font-medium">{a.quizTitle}</p>
                <p className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                  <span className={`rounded-md px-2 py-0.5 text-xs font-medium ${TONES[badge.tone]}`}>{badge.text}</span>
                  <span>{a.schoolClassName}</span>
                  <span>
                    Termen: {formatDate(a.dueAt)} ({dueCountdown(a.dueAt, now)})
                  </span>
                </p>
              </div>
              {done && a.attemptId !== null ? (
                <Link
                  to={linkToResult(a.attemptId)}
                  className={buttonVariants({ size: 'sm', variant: 'outline' })}
                >
                  Vezi rezultatul
                </Link>
              ) : readOnly ? null : (
                <Link to={`/quizzes/${a.quizId}/take`} className={buttonVariants({ size: 'sm' })}>
                  {a.state === 'IN_PROGRESS' ? 'Continuă' : 'Începe'}
                </Link>
              )}
            </CardContent>
          </Card>
        )
      })}
    </section>
  )
}
