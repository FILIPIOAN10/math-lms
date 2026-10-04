import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { AttemptStatusBadge } from '@/components/AttemptStatusBadge'
import { getMyAttempts, getStudentQuizzes, type MyAttemptDto, type QuizSummary } from '@/lib/api'
import { errorMessage } from '@/lib/errors'

function formatDate(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('ro-RO', { dateStyle: 'medium', timeStyle: 'short' }) : '—'
}

/** The student's quiz hub: published quizzes to take + their own attempt history. */
export function StudentQuizListPage() {
  const [quizzes, setQuizzes] = useState<QuizSummary[]>([])
  const [attempts, setAttempts] = useState<MyAttemptDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    Promise.all([getStudentQuizzes(), getMyAttempts()])
      .then(([q, a]) => {
        if (!cancelled) {
          setQuizzes(q)
          setAttempts(a)
        }
      })
      .catch((e) => !cancelled && setError(errorMessage(e)))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [])

  const inProgressQuizIds = new Set(
    attempts.filter((a) => a.status === 'IN_PROGRESS').map((a) => a.quizId),
  )

  return (
    <div className="min-h-screen bg-muted p-4">
      <div className="mx-auto max-w-3xl space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold">Testele mele</h1>
          <Link to="/" className={buttonVariants({ variant: 'outline', size: 'sm' })}>Acasă</Link>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}
        {loading && <p className="text-muted-foreground">Se încarcă...</p>}

        {!loading && (
          <>
            <section className="space-y-2">
              <h2 className="font-medium">Teste disponibile</h2>
              {quizzes.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  Niciun test publicat momentan. Revino după ce profesorul publică unul.
                </p>
              ) : (
                quizzes.map((q) => (
                  <Card key={q.id}>
                    <CardContent data-testid="quiz-card" className="flex items-center justify-between gap-3 py-3">
                      <div className="min-w-0">
                        <p className="truncate font-medium">{q.title}</p>
                        {q.description && (
                          <p className="truncate text-sm text-muted-foreground">{q.description}</p>
                        )}
                      </div>
                      <Link to={`/quizzes/${q.id}/take`} data-testid="quiz-open" className={buttonVariants({ size: 'sm' })}>
                        {inProgressQuizIds.has(q.id) ? 'Continuă' : 'Începe'}
                      </Link>
                    </CardContent>
                  </Card>
                ))
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
                          <AttemptStatusBadge status={a.status} />
                          <span>{formatDate(a.submittedAt ?? a.startedAt)}</span>
                          {a.score !== null && <span className="text-foreground">{a.score} puncte</span>}
                        </p>
                      </div>
                      {a.status === 'IN_PROGRESS' ? (
                        <Link to={`/quizzes/${a.quizId}/take`} className={buttonVariants({ size: 'sm', variant: 'secondary' })}>
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
