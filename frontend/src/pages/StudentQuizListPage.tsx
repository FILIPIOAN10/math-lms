import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { ErrorState } from '@/components/ErrorState'
import { Card, CardContent } from '@/components/ui/card'
import { AttemptModeBadge } from '@/components/AttemptModeBadge'
import { AttemptStatusBadge } from '@/components/AttemptStatusBadge'
import { NewResultBadge } from '@/components/NewResultBadge'
import {
  getMyAssignments,
  getMyAttempts,
  getStudentQuizzes,
  type MyAttemptDto,
  type OfferedQuiz,
  type StudentAssignment,
} from '@/lib/api'
import { assignmentBadge, BADGE_TONES, dueCountdown, isHandedIn, type BadgeTone } from '@/lib/assignments'
import { errorMessage } from '@/lib/errors'
import { formatDate, formatDay, formatMinutes } from '@/lib/format'
import { formatGrade, GRADE_TEXT, gradeBand, romanianGrade, scoreLine } from '@/lib/grades'
import { buildQuizCards, type QuizCard } from '@/lib/quizCards'
import { cn } from '@/lib/utils'
import { PageHeader } from '@/components/PageHeader'
import { LoadingCards } from '@/components/LoadingCards'

function StatePill({ tone, children }: { tone: BadgeTone; children: string }) {
  return <span data-testid="quiz-state" className={`rounded-md px-2 py-0.5 text-xs font-medium ${BADGE_TONES[tone]}`}>{children}</span>
}

/**
 * The pill of a card; homework has its own words for a deadline passed or a late hand-in. A retake of homework already
 * handed in is simply a test in progress: "Predată" and the deadline belong to the first hand-in.
 */
function statePill(card: QuizCard) {
  const open = card.state === 'todo' || card.state === 'in-progress'
  if (open && card.assignment && !isHandedIn(card.assignment)) {
    const badge = assignmentBadge(card.assignment)
    return <StatePill tone={badge.tone}>{badge.text}</StatePill>
  }
  switch (card.state) {
    case 'in-progress': return <StatePill tone="info">În lucru</StatePill>
    case 'todo': return <StatePill tone="info">De făcut</StatePill>
    case 'waiting': return <StatePill tone="info">În corectare</StatePill>
    case 'graded': return <StatePill tone="success">Notat</StatePill>
    case 'practised': return <StatePill tone="info">Exersat</StatePill>
  }
}

/** Where the student opens one attempt from the list of them: its result, or back into it while it is open. */
function attemptLink(a: MyAttemptDto) {
  return a.status === 'IN_PROGRESS'
    ? `/quizzes/${a.quizId}/take${a.mode === 'PRACTICE' ? '?mode=practice' : ''}`
    : `/quizzes/attempts/${a.attemptId}/result`
}

/** Every attempt at a quiz, folded away: the card itself says how it stands, this is for looking back. */
function AttemptHistory({ attempts }: { attempts: MyAttemptDto[] }) {
  return (
    <details data-testid="quiz-history" className="w-full text-sm">
      <summary className="cursor-pointer text-muted-foreground hover:text-foreground">
        Toate încercările ({attempts.length})
      </summary>
      <ul className="mt-2 divide-y">
        {attempts.map((a) => (
          <li key={a.attemptId} className="flex flex-wrap items-center justify-between gap-2 py-1.5">
            <span className="flex flex-wrap items-center gap-2 text-muted-foreground">
              {a.mode === 'PRACTICE' ? <AttemptModeBadge mode={a.mode} /> : <AttemptStatusBadge status={a.status} />}
              <span>{formatDate(a.submittedAt ?? a.startedAt)}</span>
              {a.score !== null && <span className="text-foreground">{scoreLine(a.score, a.maxScore)}</span>}
            </span>
            <Link to={attemptLink(a)} className="font-medium underline-offset-2 hover:underline">
              {a.status === 'IN_PROGRESS' ? 'Reia' : 'Deschide'}
            </Link>
          </li>
        ))}
      </ul>
    </details>
  )
}

/**
 * One quiz, whatever it is to the student (homework, a free test, something already graded), and what to do next: its
 * title, one line and the grade. What it was about only matters while it is still to do; the points are on the result.
 */
function QuizCardView({ card, now, showClass }: { card: QuizCard; now: number; showClass: boolean }) {
  const { assignment, shown, counted } = card
  const open = card.state === 'todo' || card.state === 'in-progress'
  const grade = card.state === 'graded' && counted?.score != null ? romanianGrade(counted.score, counted.maxScore) : null
  const takeUrl = `/quizzes/${card.quizId}/take`
  const onlyAttempt = card.attempts.length === 1 && card.attempts[0] === shown

  return (
    <Card className="hover-lift">
      {/* On a phone the grade and the buttons go under the title instead of squeezing it. */}
      <CardContent data-testid="quiz-card" className="flex flex-wrap items-center justify-between gap-3 py-3">
        <div className="min-w-0 flex-1 basis-56 space-y-1">
          <p className="truncate font-medium">{card.title}</p>
          {open && card.description && <p className="truncate text-sm text-muted-foreground">{card.description}</p>}
          <p className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            {statePill(card)}
            {card.newResult && <NewResultBadge />}
            {!open && assignment?.late && <StatePill tone="warning">Predată cu întârziere</StatePill>}
            {showClass && assignment && <span>{assignment.schoolClassName}</span>}
            {open && assignment && !isHandedIn(assignment) && (
              <span>Termen: {formatDate(assignment.dueAt)} ({dueCountdown(assignment.dueAt, now)})</span>
            )}
            {!open && shown?.submittedAt && <span>{formatDay(shown.submittedAt)}</span>}
            {card.maxAttempts !== null && card.maxAttempts > 1 && card.attemptsUsed > 0 && (
              <span data-testid="quiz-attempts">{card.attemptsUsed} din {card.maxAttempts} încercări</span>
            )}
            {open && card.timeLimitMinutes !== null && (
              <span data-testid="quiz-time-limit" className="text-xs font-medium">⏱ {formatMinutes(card.timeLimitMinutes)}</span>
            )}
          </p>
        </div>
        <div data-testid="quiz-actions" className="flex max-w-full min-w-0 flex-wrap items-center gap-2">
          {grade !== null && (
            <span data-testid="quiz-grade" className="mr-1 text-sm text-muted-foreground">
              nota <span className={cn('text-lg font-semibold tabular-nums', GRADE_TEXT[gradeBand(grade)])}>{formatGrade(grade)}</span>
              {card.gradedCount > 1 && <span className="ml-1 text-xs">cea mai mare din {card.gradedCount}</span>}
            </span>
          )}
          {card.practiceAllowed && (
            <Link
              to={`${takeUrl}?mode=practice`}
              data-testid="quiz-practice"
              className={buttonVariants({ size: 'sm', variant: 'outline' })}
            >
              {card.practiceOpen ? 'Continuă exersarea' : 'Exersează'}
            </Link>
          )}
          {open ? (
            <Link to={takeUrl} data-testid="quiz-open" className={buttonVariants({ size: 'sm' })}>
              {card.state === 'in-progress' ? 'Continuă' : 'Începe'}
            </Link>
          ) : (
            <>
              {card.available && card.state !== 'waiting' && card.blockedBy === null && (
                <Link to={takeUrl} data-testid="quiz-open" className={buttonVariants({ size: 'sm', variant: 'outline' })}>
                  Dă din nou
                </Link>
              )}
              {shown && (
                <Link to={`/quizzes/attempts/${shown.attemptId}/result`} className={buttonVariants({ size: 'sm' })}>
                  Vezi rezultatul
                </Link>
              )}
            </>
          )}
        </div>
        {card.attempts.length > 0 && !onlyAttempt && <AttemptHistory attempts={card.attempts} />}
      </CardContent>
    </Card>
  )
}

/**
 * The student's tests, one card per quiz however many ways it reached them: homework, a test open to take, their
 * attempts. "De făcut" comes first, then what they already handed in; older attempts fold away inside each card.
 */
export function StudentQuizListPage() {
  const [quizzes, setQuizzes] = useState<OfferedQuiz[]>([])
  const [attempts, setAttempts] = useState<MyAttemptDto[]>([])
  const [assignments, setAssignments] = useState<StudentAssignment[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [loadedAt] = useState(() => Date.now()) // deadlines count down from when the list was shown
  // Set by "Salvează și ieși" in a test: the work it saved, so the student knows it is safe and where to pick it up.
  // Read once, then dropped from the history entry, so coming Back here later does not repeat it.
  const location = useLocation()
  const navigate = useNavigate()
  const [savedQuizTitle] = useState(() => (location.state as { savedQuizTitle?: string } | null)?.savedQuizTitle ?? null)
  useEffect(() => {
    if (savedQuizTitle) navigate(location.pathname, { replace: true, state: null })
  }, [savedQuizTitle, navigate, location.pathname])

  useEffect(() => {
    let cancelled = false
    Promise.all([getStudentQuizzes(), getMyAttempts(), getMyAssignments()])
      .then(([q, a, hw]) => {
        if (!cancelled) {
          setQuizzes(q)
          setAttempts(a)
          setAssignments(hw)
        }
      })
      .catch((e) => !cancelled && setError(errorMessage(e)))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [])

  const { todo, done } = buildQuizCards(quizzes, assignments, attempts)
  // Naming the class says nothing to a student who has only one.
  const showClass = new Set(assignments.map((a) => a.schoolClassName)).size > 1

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-6">
        <PageHeader title="Testele mele" description="Ce ai de făcut și ce ai dat până acum." />

        {savedQuizTitle && (
          <p
            role="status"
            data-testid="saved-notice"
            className="rounded-lg border border-emerald-500/40 bg-emerald-500/10 p-3 text-sm text-emerald-900 dark:text-emerald-100"
          >
            Am salvat ce ai lucrat la „{savedQuizTitle}”. Continui oricând cu „Continuă”.
          </p>
        )}
        {error && <ErrorState message={error} />}
        {loading && <LoadingCards />}

        {!loading && (
          <>
            <section className="space-y-2" data-testid="quizzes-todo">
              <h2 className="font-medium">De făcut</h2>
              {todo.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  {done.length === 0
                    ? 'Niciun test publicat momentan. Revino după ce profesorul publică unul.'
                    : 'Nimic de făcut acum.'}
                </p>
              ) : (
                todo.map((card) => <QuizCardView key={card.quizId} card={card} now={loadedAt} showClass={showClass} />)
              )}
            </section>

            <section className="space-y-2" data-testid="quizzes-done">
              <h2 className="font-medium">Teste date</h2>
              {done.length === 0 ? (
                <p className="text-sm text-muted-foreground">Nu ai dat încă niciun test.</p>
              ) : (
                done.map((card) => <QuizCardView key={card.quizId} card={card} now={loadedAt} showClass={showClass} />)
              )}
            </section>
          </>
        )}
      </div>
    </div>
  )
}
