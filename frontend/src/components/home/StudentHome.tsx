import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { ErrorState } from '@/components/ErrorState'
import { Card, CardContent } from '@/components/ui/card'
import { AssignmentsSection } from '@/components/AssignmentsSection'
import { HomeColumns, HomeEmpty, HomeSection } from '@/components/home/HomeBits'
import {
  getMyAssignments,
  getMyAttempts,
  getMyProgress,
  getReviewToday,
  type MyAttemptDto,
  type ProgressPointDto,
  type ReviewToday,
  type StudentAssignment,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatDate, roCount } from '@/lib/format'
import { scoreLine } from '@/lib/grades'
import { EmptyArt, StreakPill } from '@/components/EmptyArt'
import { GradeFigure } from '@/components/GradeFigure'
import { NewResultBadge } from '@/components/NewResultBadge'
import { latestGrade, todoAssignments } from '@/lib/home'
import { LoadingCards } from '@/components/LoadingCards'

interface StudentData {
  assignments: StudentAssignment[]
  attempts: MyAttemptDto[]
  progress: ProgressPointDto[]
}

/** New grades, in one row however many there are: they are news, and must not push the homework off the screen. */
function NewGrades({ results }: { results: MyAttemptDto[] }) {
  const only = results.length === 1 ? results[0] : null
  return (
    <Card data-testid="home-new-result">
      <CardContent className="flex flex-wrap items-center justify-between gap-3 py-3">
        <div className="min-w-0 flex-1 basis-48 space-y-1">
          <p className="flex items-center gap-2">
            <NewResultBadge />
            <span className="truncate font-medium">{only ? only.quizTitle : roCount(results.length, 'notă nouă', 'note noi')}</span>
          </p>
          <p className="truncate text-sm text-muted-foreground">
            {only
              ? only.score !== null && scoreLine(only.score, only.maxScore)
              : results.map((a) => a.quizTitle).join(' · ')}
          </p>
        </div>
        <Link to={only ? `/quizzes/attempts/${only.attemptId}/result` : '/quizzes'} className={buttonVariants({ size: 'sm' })}>
          {only ? 'Vezi rezultatul' : 'Vezi notele'}
        </Link>
      </CardContent>
    </Card>
  )
}

/**
 * The student's start page answers "what do I do now?": homework still to hand in (soonest first), tests started
 * and not handed in, then any new grades (above them only when there is nothing to do), how the last graded test went
 * and how many wait for the teacher.
 */
export function StudentHome() {
  const [data, setData] = useState<StudentData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [review, setReview] = useState<ReviewToday | null>(null)

  useEffect(() => {
    let cancelled = false
    Promise.all([getMyAssignments(), getMyAttempts(), getMyProgress()])
      .then(([assignments, attempts, progress]) => !cancelled && setData({ assignments, attempts, progress }))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    getReviewToday()
      .then((r) => !cancelled && setReview(r))
      .catch(() => undefined) // the home still works without the review card
    return () => {
      cancelled = true
    }
  }, [])

  if (error) return <ErrorState message={error} />
  if (!data) return <LoadingCards />

  const todo = todoAssignments(data.assignments)
  const homeworkQuizIds = new Set(todo.map((a) => a.quizId))
  // Homework in progress already has its own "Continuă"; practice sessions are not something left unfinished.
  const started = data.attempts.filter(
    (a) => a.mode === 'TEST' && a.status === 'IN_PROGRESS' && !homeworkQuizIds.has(a.quizId),
  )
  const awaiting = data.attempts.filter((a) => a.mode === 'TEST' && a.status === 'SUBMITTED').length
  const last = latestGrade(data.progress)

  const newResults = data.attempts.filter((a) => a.newResult)
  const nothingToDo = todo.length === 0 && started.length === 0
  const grades = newResults.length > 0 ? <NewGrades results={newResults} /> : null

  return (
    <HomeColumns
      main={
        <>
      {nothingToDo && grades}
      {todo.length > 0 ? (
        <AssignmentsSection assignments={todo} title="De făcut" hero />
      ) : (
        <HomeSection title="De făcut">
          <HomeEmpty
            symbol="Σ"
            action={
              <Link to="/quizzes" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
                Vezi toate testele
              </Link>
            }
          >
            Nicio temă de făcut acum. 🎉
          </HomeEmpty>
        </HomeSection>
      )}
      {started.length > 0 && (
        <HomeSection title="Teste începute">
          {started.map((a) => (
            <Card key={a.attemptId}>
              <CardContent className="flex items-center justify-between gap-3 py-3">
                <div className="min-w-0">
                  <p className="truncate font-medium">{a.quizTitle}</p>
                  <p className="text-sm text-muted-foreground">Început: {formatDate(a.startedAt)}</p>
                </div>
                <Link to={`/quizzes/${a.quizId}/take`} className={buttonVariants({ size: 'sm' })}>
                  Continuă
                </Link>
              </CardContent>
            </Card>
          ))}
        </HomeSection>
      )}
      {!nothingToDo && grades}
        </>
      }
      side={
        <>
      {review && review.totalCards > 0 && (
        <HomeSection title="Recapitulare">
          <Card data-testid="home-review">
            <CardContent className="flex flex-wrap items-center justify-between gap-3 py-4 text-sm">
              {review.remainingToday > 0 ? (
                <p className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">{roCount(review.remainingToday, 'întrebare', 'întrebări')} de recapitulat azi</span>
                  {review.streak > 0 && <StreakPill>seria {roCount(review.streak, 'zi', 'zile')}</StreakPill>}
                </p>
              ) : (
                <p className="flex flex-wrap items-center gap-2">
                  <span className="font-medium text-emerald-700 dark:text-emerald-300">Gata pe azi ✓</span>
                  {review.streak > 0 && <StreakPill>seria {roCount(review.streak, 'zi', 'zile')}</StreakPill>}
                </p>
              )}
              {review.remainingToday > 0 && (
                <Link to="/review" className={buttonVariants({ size: 'sm' })}>Începe recapitularea</Link>
              )}
            </CardContent>
          </Card>
        </HomeSection>
      )}
      <HomeSection
        title="Rezultate"
        action={
          <Link to="/progress" className={buttonVariants({ variant: 'ghost', size: 'sm' })}>
            Vezi progresul
          </Link>
        }
      >
        {/* Nothing waiting is a quiet line under the grade, not a card with a big 0 next to it. */}
        <div className={awaiting > 0 ? 'grid gap-3 sm:grid-cols-[2fr_1fr]' : 'space-y-2'}>
          <Card data-testid="latest-grade">
            <CardContent className="space-y-1 py-4">
              <p className="text-xs font-medium text-muted-foreground">Ultima notă</p>
              {last ? (
                <>
                  <GradeFigure score={last.score} maxScore={last.maxScore} />
                  <p className="flex flex-wrap items-center justify-between gap-2 text-sm text-muted-foreground">
                    <span className="min-w-0 truncate">
                      {last.quizTitle} · {formatDate(last.submittedAt)}
                    </span>
                    <Link
                      to={`/quizzes/attempts/${last.attemptId}/result`}
                      className={buttonVariants({ variant: 'outline', size: 'sm' })}
                    >
                      Vezi rezultatul
                    </Link>
                  </p>
                </>
              ) : (
                <div className="flex items-center gap-3">
                  {/* The plain x², not a number: anything numeric in this spot reads as a grade. */}
                  <EmptyArt />
                  <p className="text-sm text-muted-foreground">Nicio notă încă. Apare după primul test corectat.</p>
                </div>
              )}
            </CardContent>
          </Card>
          {awaiting > 0 ? (
            <Card>
              <CardContent className="space-y-1 py-4">
                <p className="text-xs font-medium text-muted-foreground">În corectare</p>
                <p data-testid="awaiting-grading" className="text-3xl font-semibold">{awaiting}</p>
                <p className="text-sm text-muted-foreground">Le corectează profesorul.</p>
              </CardContent>
            </Card>
          ) : (
            <p className="text-sm text-muted-foreground">Nimic nu așteaptă corectura.</p>
          )}
        </div>
      </HomeSection>
        </>
      }
    />
  )
}
