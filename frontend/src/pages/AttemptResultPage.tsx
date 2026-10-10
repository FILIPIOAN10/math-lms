import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AttemptResultView } from '@/components/AttemptResultView'
import { getAttemptResult, type AttemptResultViewDto } from '@/lib/api'
import { errorMessage } from '@/lib/errors'

/** The student's own result (Q8 backend): score, per-item right/wrong, and the barem after submit. */
export function AttemptResultPage() {
  const { attemptId } = useParams<{ attemptId: string }>()
  const [result, setResult] = useState<AttemptResultViewDto | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getAttemptResult(Number(attemptId))
      .then((r) => !cancelled && setResult(r))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [attemptId])

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <div className="flex items-center justify-between">
          <Link to="/quizzes" className="text-sm text-muted-foreground hover:text-foreground hover:underline">
            ← Testele mele
          </Link>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}
        {!result && !error && <p className="text-muted-foreground">Se încarcă...</p>}

        {result && <AttemptResultView result={result} />}
      </div>
    </div>
  )
}
