import { useEffect, useState } from 'react'
import { Card, CardContent } from '@/components/ui/card'
import { ProgressChart } from '@/components/ProgressChart'
import { getMyProgress, type ProgressPointDto } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { PageHeader } from '@/components/PageHeader'

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
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <PageHeader title="Progresul meu" description="Testele notate, în ordinea în care le-ai dat." />

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
