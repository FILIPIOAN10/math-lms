import { useEffect, useState } from 'react'
import { Card, CardContent } from '@/components/ui/card'
import { ProgressChart } from '@/components/ProgressChart'
import { getMyMastery, getMyProgress, type ChapterMastery, type ProgressPointDto } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { PageHeader } from '@/components/PageHeader'
import { HomeSection } from '@/components/home/HomeBits'
import { MasteryList } from '@/components/MasteryList'

/** The student's own progress: their level per chapter (tests and practice), and how every graded test went over time. */
export function StudentProgressPage() {
  const [points, setPoints] = useState<ProgressPointDto[] | null>(null)
  const [mastery, setMastery] = useState<ChapterMastery[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getMyProgress()
      .then((p) => !cancelled && setPoints(p))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    getMyMastery()
      .then((m) => !cancelled && setMastery(m))
      .catch(() => !cancelled && setMastery([]))
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <PageHeader title="Progresul meu" description="Cât de bine stăpânești fiecare capitol și cum au mers testele notate." />

        {mastery && (
          <HomeSection title="Pe capitole">
            <Card>
              <CardContent className="py-4">
                <MasteryList rows={mastery} />
              </CardContent>
            </Card>
          </HomeSection>
        )}

        {error && <p className="text-sm text-destructive">{error}</p>}
        {!points && !error && <p className="text-muted-foreground">Se încarcă...</p>}
        {points && (
          <HomeSection title="Testele notate">
            <Card>
              <CardContent className="py-4">
                <ProgressChart points={points} />
              </CardContent>
            </Card>
          </HomeSection>
        )}
      </div>
    </div>
  )
}
