import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { AttemptStatusBadge } from '@/components/AttemptStatusBadge'
import { ProgressChart } from '@/components/ProgressChart'
import { getChildAttempts, getChildProgress, type MyAttemptDto, type ProgressPointDto } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'

/** One child's attempts (read-only). A child that is not yours answers 403, shown as the error. */
export function ParentChildPage() {
  const { childId } = useParams<{ childId: string }>()
  const [attempts, setAttempts] = useState<MyAttemptDto[] | null>(null)
  const [progress, setProgress] = useState<ProgressPointDto[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    Promise.all([getChildAttempts(Number(childId)), getChildProgress(Number(childId))])
      .then(([a, p]) => {
        if (!cancelled) {
          setAttempts(a)
          setProgress(p)
        }
      })
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [childId])

  return (
    <div className="min-h-screen bg-muted p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <div className="flex items-center justify-between">
          <Link to="/parent" className="text-sm text-muted-foreground hover:text-foreground hover:underline">
            ← Copiii mei
          </Link>
          <Link to="/" className={buttonVariants({ variant: 'outline', size: 'sm' })}>Acasă</Link>
        </div>

        <h1 className="text-xl font-semibold">Testele copilului</h1>

        {error && <p className="text-sm text-destructive">{error}</p>}
        {!attempts && !error && <p className="text-muted-foreground">Se încarcă...</p>}
        {progress && (
          <Card>
            <CardContent className="space-y-2 py-4">
              <h2 className="font-medium">Progres în timp</h2>
              <ProgressChart points={progress} />
            </CardContent>
          </Card>
        )}
        {attempts && attempts.length === 0 && (
          <p className="text-sm text-muted-foreground">Copilul nu a dat încă niciun test.</p>
        )}

        {attempts?.map((a) => (
          <Card key={a.attemptId}>
            <CardContent className="flex items-center justify-between gap-3 py-3" data-testid="child-attempt">
              <div className="min-w-0 space-y-1">
                <p className="truncate font-medium">{a.quizTitle}</p>
                <p className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                  <AttemptStatusBadge status={a.status} />
                  <span>{formatDate(a.submittedAt ?? a.startedAt)}</span>
                  {a.score !== null && <span className="text-foreground">{a.score} puncte</span>}
                </p>
              </div>
              {a.status !== 'IN_PROGRESS' && (
                <Link
                  to={`/parent/children/${childId}/attempts/${a.attemptId}`}
                  className={buttonVariants({ size: 'sm', variant: 'outline' })}
                >
                  Vezi rezultatul
                </Link>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
