import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { AttemptResultView } from '@/components/AttemptResultView'
import { getChildAttemptResult, type AttemptResultViewDto } from '@/lib/api'
import { errorMessage } from '@/lib/errors'

/** A child's result as the parent sees it — same view as the student's, worded for the parent. */
export function ParentAttemptResultPage() {
  const { childId, attemptId } = useParams<{ childId: string; attemptId: string }>()
  const [result, setResult] = useState<AttemptResultViewDto | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getChildAttemptResult(Number(childId), Number(attemptId))
      .then((r) => !cancelled && setResult(r))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [childId, attemptId])

  return (
    <div className="min-h-screen bg-muted p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <div className="flex items-center justify-between">
          <Link
            to={`/parent/children/${childId}`}
            className="text-sm text-muted-foreground hover:text-foreground hover:underline"
          >
            ← Testele copilului
          </Link>
          <Link to="/" className={buttonVariants({ variant: 'outline', size: 'sm' })}>Acasă</Link>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}
        {!result && !error && <p className="text-muted-foreground">Se încarcă...</p>}

        {result && <AttemptResultView result={result} audience="parent" />}
      </div>
    </div>
  )
}
