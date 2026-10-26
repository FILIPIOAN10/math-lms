import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AttemptResultView } from '@/components/AttemptResultView'
import { getAttemptResult, markResultSeen, type AttemptResultViewDto } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { NAV_COUNTS_CHANGED } from '@/lib/roles'
import { usePageTitle } from '@/lib/usePageTitle'
import { LoadingCards } from '@/components/LoadingCards'

/** The student's own result (Q8 backend): score, per-item right/wrong, and the barem after submit. */
export function AttemptResultPage() {
  const { attemptId } = useParams<{ attemptId: string }>()
  const [result, setResult] = useState<AttemptResultViewDto | null>(null)
  usePageTitle(result ? `Rezultat · ${result.quizTitle}` : 'Rezultat')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getAttemptResult(Number(attemptId))
      .then((r) => {
        if (cancelled) return
        setResult(r)
        // S3: a graded test opened here is no longer "Nou" - and the number next to "Testele mele" drops now.
        if (r.mode === 'TEST' && r.status === 'GRADED') {
          markResultSeen(r.attemptId)
            .then(() => window.dispatchEvent(new Event(NAV_COUNTS_CHANGED)))
            .catch(() => undefined) // only a hint: the result itself is already on screen
        }
      })
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
        {!result && !error && <LoadingCards />}

        {result && <AttemptResultView result={result} />}
      </div>
    </div>
  )
}
