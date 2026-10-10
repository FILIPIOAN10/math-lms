import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { AssignmentsSection } from '@/components/AssignmentsSection'
import { HomeEmpty, HomeSection } from '@/components/home/HomeBits'
import {
  getMyAssignments,
  getMyAttempts,
  getMyProgress,
  type MyAttemptDto,
  type ProgressPointDto,
  type StudentAssignment,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { formatGrade, romanianGrade } from '@/lib/grades'
import { latestGrade, todoAssignments } from '@/lib/home'

interface StudentData {
  assignments: StudentAssignment[]
  attempts: MyAttemptDto[]
  progress: ProgressPointDto[]
}

/**
 * The student's start page answers "what do I do now?": homework still to hand in (soonest first), tests started
 * and not handed in, then how the last graded test went and how many wait for the teacher.
 */
export function StudentHome() {
  const [data, setData] = useState<StudentData | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    Promise.all([getMyAssignments(), getMyAttempts(), getMyProgress()])
      .then(([assignments, attempts, progress]) => !cancelled && setData({ assignments, attempts, progress }))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [])

  if (error) return <p className="text-sm text-destructive">{error}</p>
  if (!data) return <p className="text-muted-foreground">Se încarcă...</p>

  const todo = todoAssignments(data.assignments)
  const homeworkQuizIds = new Set(todo.map((a) => a.quizId))
  // Homework in progress already has its own "Continuă"; practice sessions are not something left unfinished.
  const started = data.attempts.filter(
    (a) => a.mode === 'TEST' && a.status === 'IN_PROGRESS' && !homeworkQuizIds.has(a.quizId),
  )
  const awaiting = data.attempts.filter((a) => a.mode === 'TEST' && a.status === 'SUBMITTED').length
  const last = latestGrade(data.progress)

  return (
    <>
      {todo.length > 0 ? (
        <AssignmentsSection assignments={todo} title="De făcut" />
      ) : (
        <HomeSection title="De făcut">
          <HomeEmpty
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

      <HomeSection
        title="Rezultate"
        action={
          <Link to="/progress" className={buttonVariants({ variant: 'ghost', size: 'sm' })}>
            Vezi progresul
          </Link>
        }
      >
        <div className="grid gap-3 sm:grid-cols-[2fr_1fr]">
          <Card data-testid="latest-grade">
            <CardContent className="space-y-1 py-4">
              <p className="text-xs font-medium text-muted-foreground">Ultima notă</p>
              {last ? (
                <>
                  <p className="text-3xl font-semibold">
                    {last.score} <span className="text-base text-muted-foreground">/ {last.maxScore} puncte</span>
                    {romanianGrade(last.score, last.maxScore) !== null && (
                      <span className="ml-2 text-base font-medium text-muted-foreground">
                        · nota {formatGrade(romanianGrade(last.score, last.maxScore)!)}
                      </span>
                    )}
                  </p>
                  <p className="flex flex-wrap items-center justify-between gap-2 text-sm text-muted-foreground">
                    <span className="min-w-0 truncate">
                      {last.quizTitle} · {last.percent}% · {formatDate(last.submittedAt)}
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
                <p className="text-sm text-muted-foreground">Nicio notă încă. Apare după primul test corectat.</p>
              )}
            </CardContent>
          </Card>
          <Card>
            <CardContent className="space-y-1 py-4">
              <p className="text-xs font-medium text-muted-foreground">În corectare</p>
              <p data-testid="awaiting-grading" className="text-3xl font-semibold">{awaiting}</p>
              <p className="text-sm text-muted-foreground">
                {awaiting === 0 ? 'Nimic nu așteaptă profesorul.' : 'Le corectează profesorul.'}
              </p>
            </CardContent>
          </Card>
        </div>
      </HomeSection>
    </>
  )
}
