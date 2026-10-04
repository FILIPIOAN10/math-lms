import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { ProgressChart } from '@/components/ProgressChart'
import { getMyProgress, type ProgressPointDto } from '@/lib/api'
import { errorMessage } from '@/lib/errors'

/** The student's own progress: how every graded test went, over time. */
export function StudentProgressPage() {
  const [points, setPoints] = useState<ProgressPointDto[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getMyProgress()
      .then((p) => !cancelled && setPoints(p))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="min-h-screen bg-muted p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <div className="flex items-center justify-between">
          <Link to="/quizzes" className="text-sm text-muted-foreground hover:text-foreground hover:underline">
            ← Testele mele
          </Link>
          <Link to="/" className={buttonVariants({ variant: 'outline', size: 'sm' })}>Acasă</Link>
        </div>

        <h1 className="text-2xl font-semibold">Progresul meu</h1>

        {error && <p className="text-sm text-destructive">{error}</p>}
        {!points && !error && <p className="text-muted-foreground">Se încarcă...</p>}
        {points && (
          <Card>
            <CardContent className="py-4">
              <ProgressChart points={points} />
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  )
}
