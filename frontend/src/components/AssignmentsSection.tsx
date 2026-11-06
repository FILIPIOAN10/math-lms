import { useId, useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { HeroCard } from '@/components/HeroCard'
import { ClockLine, ClockPill } from '@/components/TestClock'
import { clockNote } from '@/lib/countdown'
import { assignmentBadge, BADGE_TONES, dueCountdown, isHandedIn } from '@/lib/assignments'
import { formatDate } from '@/lib/format'
import { formatGrade, GRADE_TEXT, gradeBand, romanianGrade } from '@/lib/grades'
import { cn } from '@/lib/utils'
import { type MyAttemptDto, type StudentAssignment } from '@/lib/api'

/**
 * The student's homework: quizzes their teacher assigned to their class with a deadline. The deadline is soft - an
 * overdue assignment can still be done, it is only flagged. The list arrives ordered (not done first, soonest first).
 *
 * With `childId` it is the parent's read-only view of that child's homework: no buttons to start or continue, and a
 * handed-in one links to the parent's page for the result. `resultHref` makes it read-only the same way for the
 * teacher's page of one student, with its own link to the result. `emptyText` shows a line instead of hiding an empty
 * list.
 *
 * A handed-in homework no longer counts down to its deadline: with the student's `attempts` at hand it says when it was
 * handed in and, once graded, its grade - the first thing a parent looks for.
 *
 * A timed homework the student started and left counts down its own clock instead (`clocks`: seconds left per open
 * attempt): the paper is handed in when that runs out, maybe days before the homework's deadline.
 */
export function AssignmentsSection({
  assignments,
  attempts = [],
  nowMs,
  title = 'Teme',
  childId,
  resultHref,
  emptyText,
  hero = false,
  clocks,
}: {
  assignments: StudentAssignment[]
  attempts?: Pick<MyAttemptDto, 'attemptId' | 'score' | 'maxScore' | 'submittedAt'>[]
  nowMs?: number
  title?: string
  childId?: number
  resultHref?: (attemptId: number) => string
  emptyText?: string
  /**
   * The page's one big card: what to do next - a timed homework whose clock runs, else the first homework still to do.
   * Only for the student's own list.
   */
  hero?: boolean
  /** Seconds left on each open timed attempt, by attempt id. */
  clocks?: ReadonlyMap<number, number>
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

  const clockOf = (a: StudentAssignment) =>
    a.state === 'IN_PROGRESS' && a.attemptId !== null ? clocks?.get(a.attemptId) ?? null : null
  // A clock that is still running beats any deadline: the soonest to run out is what the student must go back to.
  const running = assignments
    .filter((a) => (clockOf(a) ?? 0) > 0)
    .sort((x, y) => (clockOf(x) ?? 0) - (clockOf(y) ?? 0))
  // A test whose time is up hands itself in: nothing to do there, so it never takes the big card.
  const heroItem = hero && !readOnly
    ? running[0] ?? assignments.find((a) => !isHandedIn(a) && clockOf(a) !== 0)
    : undefined
  const ordered = heroItem ? [heroItem, ...assignments.filter((a) => a !== heroItem)] : assignments

  return (
    <section className="space-y-2" data-testid="assignments" aria-labelledby={headingId}>
      <h2 id={headingId} className="font-medium">{title}</h2>
      {assignments.length === 0 && <p className="text-sm text-muted-foreground">{emptyText}</p>}
      {ordered.map((a) => {
        const badge = assignmentBadge(a)
        const done = a.state === 'SUBMITTED' || a.state === 'GRADED'
        const attempt = done ? attempts.find((t) => t.attemptId === a.attemptId) : undefined
        const grade = a.state === 'GRADED' && attempt?.score != null ? romanianGrade(attempt.score, attempt.maxScore) : null
        const clock = clockOf(a)
        if (a === heroItem) {
          return (
            <HeroCard
              key={a.assignmentId}
              testId="assignment-card"
              kicker={clock !== null ? 'Test început' : 'Urmează'}
              title={a.quizTitle}
              meta={
                <>
                  <span className="rounded-md bg-white/20 px-2 py-0.5 text-xs font-medium text-white">{badge.text}</span>
                  <span>{a.schoolClassName}</span>
                  {clock !== null ? (
                    <ClockPill seconds={clock} />
                  ) : (
                    <span className="rounded-full bg-warm px-2 py-0.5 text-xs font-semibold text-neutral-900">
                      Termen: {formatDate(a.dueAt)} ({dueCountdown(a.dueAt, now)})
                    </span>
                  )}
                </>
              }
              note={clock !== null ? clockNote(clock) : undefined}
              action={
                <Link
                  to={`/quizzes/${a.quizId}/take`}
                  className={cn(buttonVariants({ size: 'lg' }), 'bg-white px-5 text-primary hover:bg-white/90')}
                >
                  {a.state === 'IN_PROGRESS' ? 'Continuă' : 'Începe'}
                </Link>
              }
            />
          )
        }
        return (
          <Card key={a.assignmentId}>
            {/* On a phone the grade and the button go under the title instead of squeezing it to "Procente — te…". */}
            <CardContent data-testid="assignment-card" className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="min-w-0 flex-1 basis-56 space-y-1">
                <p className="truncate font-medium">{a.quizTitle}</p>
                <p className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                  <span className={`rounded-md px-2 py-0.5 text-xs font-medium ${BADGE_TONES[badge.tone]}`}>{badge.text}</span>
                  {attempt?.submittedAt && <span>pe {formatDate(attempt.submittedAt)}</span>}
                  <span>{a.schoolClassName}</span>
                  {clock !== null ? (
                    <ClockLine seconds={clock} />
                  ) : !done ? (
                    <span>
                      Termen: {formatDate(a.dueAt)} ({dueCountdown(a.dueAt, now)})
                    </span>
                  ) : !attempt?.submittedAt && (
                    <span>Termen: {formatDate(a.dueAt)}</span>
                  )}
                </p>
              </div>
              {clock === 0 ? null : done && a.attemptId !== null ? (
                <div className="flex shrink-0 items-center gap-3">
                  {grade !== null && (
                    <span data-testid="assignment-grade" className="text-sm text-muted-foreground">
                      nota <span className={cn('text-lg font-semibold tabular-nums', GRADE_TEXT[gradeBand(grade)])}>{formatGrade(grade)}</span>
                    </span>
                  )}
                  <Link
                    to={linkToResult(a.attemptId)}
                    className={buttonVariants({ size: 'sm', variant: 'outline' })}
                  >
                    Vezi rezultatul
                  </Link>
                </div>
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
