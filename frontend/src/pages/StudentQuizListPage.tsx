import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { AssignmentsSection } from '@/components/AssignmentsSection'
import { AttemptModeBadge } from '@/components/AttemptModeBadge'
import { AttemptStatusBadge } from '@/components/AttemptStatusBadge'
import {
  getMyAssignments,
  getMyAttempts,
  getStudentQuizzes,
  type MyAttemptDto,
  type QuizSummary,
  type StudentAssignment,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatMinutes } from '@/lib/format'
import { scoreLine } from '@/lib/grades'
import { todoAssignments } from '@/lib/home'

function formatDate(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('ro-RO', { dateStyle: 'medium', timeStyle: 'short' }) : '—'
}

/** The student's quiz hub: published quizzes to take + their own attempt history. */
export function StudentQuizListPage() {
  const [quizzes, setQuizzes] = useState<QuizSummary[]>([])
  const [attempts, setAttempts] = useState<MyAttemptDto[]>([])
  const [assignments, setAssignments] = useState<StudentAssignment[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

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

  const openQuizIds = (mode: 'TEST' | 'PRACTICE') =>
    new Set(attempts.filter((a) => a.status === 'IN_PROGRESS' && a.mode === mode).map((a) => a.quizId))
  const inProgressTests = openQuizIds('TEST')
  const inProgressPractices = openQuizIds('PRACTICE')

  // The latest handed-in graded test per quiz (attempts come newest first), for the "already taken" line.
  const latestDone = new Map<number, MyAttemptDto>()
  for (const a of attempts) {
    if (a.mode === 'TEST' && a.status !== 'IN_PROGRESS' && !latestDone.has(a.quizId)) latestDone.set(a.quizId, a)
  }
  // Homework still to do is offered under "Teme"; listing it again here would show two "Începe" for one quiz.
  const homeworkToDo = new Set(todoAssignments(assignments).map((a) => a.quizId))
  const available = quizzes.filter((q) => !homeworkToDo.has(q.id))

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold">Testele mele</h1>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}
        {loading && <p className="text-muted-foreground">Se încarcă...</p>}

        {!loading && (
          <>
            <AssignmentsSection assignments={assignments} />

            <section className="space-y-2">
              <h2 className="font-medium">Teste disponibile</h2>
              {available.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  {quizzes.length === 0
                    ? 'Niciun test publicat momentan. Revino după ce profesorul publică unul.'
                    : 'Toate testele tale sunt la „Teme”.'}
                </p>
              ) : (
                available.map((q) => {
                  const done = inProgressTests.has(q.id) ? undefined : latestDone.get(q.id)
                  return (
                  <Card key={q.id}>
                    <CardContent data-testid="quiz-card" className="flex items-center justify-between gap-3 py-3">
                      <div className="min-w-0">
                        <p className="truncate font-medium">{q.title}</p>
                        {q.description && (
                          <p className="truncate text-sm text-muted-foreground">{q.description}</p>
                        )}
                        {q.timeLimitMinutes !== null && (
                          <p className="text-xs font-medium text-muted-foreground" data-testid="quiz-time-limit">
                            ⏱ {formatMinutes(q.timeLimitMinutes)}
                          </p>
                        )}
                        {done && (
                          <p data-testid="quiz-done" className="text-sm text-emerald-700 dark:text-emerald-300">
                            {done.status === 'GRADED' && done.score !== null
                              ? `✓ Dat · ${scoreLine(done.score, done.maxScore)}`
                              : '✓ Predat — în corectare'}
                          </p>
                        )}
                      </div>
                      <div className="flex shrink-0 gap-2">
                        {q.practiceAllowed && (
                          <Link
                            to={`/quizzes/${q.id}/take?mode=practice`}
                            data-testid="quiz-practice"
                            className={buttonVariants({ size: 'sm', variant: 'outline' })}
                          >
                            {inProgressPractices.has(q.id) ? 'Continuă practica' : 'Exersează'}
                          </Link>
                        )}
                        {done ? (
                          <>
                            <Link
                              to={`/quizzes/${q.id}/take`}
                              data-testid="quiz-open"
                              className={buttonVariants({ size: 'sm', variant: 'outline' })}
                            >
                              Dă din nou
                            </Link>
                            <Link to={`/quizzes/attempts/${done.attemptId}/result`} className={buttonVariants({ size: 'sm' })}>
                              Vezi rezultatul
                            </Link>
                          </>
                        ) : (
                          <Link to={`/quizzes/${q.id}/take`} data-testid="quiz-open" className={buttonVariants({ size: 'sm' })}>
                            {inProgressTests.has(q.id) ? 'Continuă' : 'Începe'}
                          </Link>
                        )}
                      </div>
                    </CardContent>
                  </Card>
                  )
                })
              )}
            </section>

            <section className="space-y-2">
              <h2 className="font-medium">Încercările mele</h2>
              {attempts.length === 0 ? (
                <p className="text-sm text-muted-foreground">Nu ai dat încă niciun test.</p>
              ) : (
                attempts.map((a) => (
                  <Card key={a.attemptId}>
                    <CardContent className="flex items-center justify-between gap-3 py-3">
                      <div className="min-w-0 space-y-1">
                        <p className="truncate font-medium">{a.quizTitle}</p>
                        <p className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                          {a.mode === 'PRACTICE' ? <AttemptModeBadge mode={a.mode} /> : <AttemptStatusBadge status={a.status} />}
                          <span>{formatDate(a.submittedAt ?? a.startedAt)}</span>
                          {a.score !== null && <span className="text-foreground">{scoreLine(a.score, a.maxScore)}</span>}
                        </p>
                      </div>
                      {a.status === 'IN_PROGRESS' ? (
                        <Link
                          to={`/quizzes/${a.quizId}/take${a.mode === 'PRACTICE' ? '?mode=practice' : ''}`}
                          className={buttonVariants({ size: 'sm', variant: 'secondary' })}
                        >
                          Continuă
                        </Link>
                      ) : (
                        <Link
                          to={`/quizzes/attempts/${a.attemptId}/result`}
                          className={buttonVariants({ size: 'sm', variant: 'outline' })}
                        >
                          Vezi rezultatul
                        </Link>
                      )}
                    </CardContent>
                  </Card>
                ))
              )}
            </section>
          </>
        )}
      </div>
    </div>
  )
}
