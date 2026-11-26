import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { HomeSection } from '@/components/home/HomeBits'
import { getChildEnPrediction, getMyEnPrediction, getStudentEnPrediction, type EnPrediction } from '@/lib/api'
import { barPercent, formatGain, formatRange } from '@/lib/enPrediction'
import { roCount } from '@/lib/format'

/**
 * „Ce notă iei la EN?": the estimated EN grade as a range on a 1 - 10 bar, and the chapters that would raise it most.
 * It loads its own data, so each page only says whose estimate to show: the student's own (no childId or
 * studentId; the chapters then link to their lessons and the empty state offers the daily review), a parent's child
 * (childId) or, for the teacher, any student (studentId). A failed load shows nothing.
 */
export function EnPredictionCard({ childId, studentId }: { childId?: number; studentId?: number }) {
  const [prediction, setPrediction] = useState<EnPrediction | null>(null)
  const own = childId === undefined && studentId === undefined

  useEffect(() => {
    let cancelled = false
    const load = childId !== undefined
      ? getChildEnPrediction(childId)
      : studentId !== undefined ? getStudentEnPrediction(studentId) : getMyEnPrediction()
    load
      .then((p) => !cancelled && setPrediction(p))
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [childId, studentId])

  if (!prediction) return null

  return (
    <HomeSection title="Nota estimată la EN">
      <Card data-testid="en-prediction">
        <CardContent className="space-y-4 py-4">
          {!prediction.available || prediction.low === null || prediction.high === null ? (
            <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
              <p className="text-muted-foreground">
                Mai rezolvă câteva întrebări:{' '}
                {roCount(prediction.answersNeeded - prediction.answers, 'răspuns', 'răspunsuri')} și apare estimarea.
              </p>
              {own && (
                <Link to="/review" className={buttonVariants({ size: 'sm' })}>
                  Începe recapitularea
                </Link>
              )}
            </div>
          ) : (
            <>
              <p data-testid="en-prediction-range" className="text-3xl font-semibold tabular-nums">
                {formatRange(prediction.low, prediction.high)}
              </p>
              <div
                role="img"
                aria-label={'Între ' + formatRange(prediction.low, prediction.high) + ', pe o scară de la 1 la 10'}
                className="relative h-2 rounded-full bg-muted"
              >
                <div
                  className="absolute inset-y-0 rounded-full bg-primary"
                  style={{
                    left: barPercent(prediction.low) + '%',
                    width: Math.max(2, barPercent(prediction.high) - barPercent(prediction.low)) + '%',
                  }}
                />
              </div>
              <div aria-hidden="true" className="flex justify-between text-xs text-muted-foreground">
                <span>1</span>
                <span>10</span>
              </div>
              {prediction.topGains.length > 0 && (
                <div className="space-y-2">
                  <p className="text-sm font-medium">Cel mai mult urci dacă exersezi:</p>
                  <ul className="flex flex-wrap gap-2">
                    {prediction.topGains.map((g) => {
                      const chip = (
                        <>
                          <span className="font-medium">{g.code}</span> {g.title}{' '}
                          <span className="font-semibold text-success-foreground">{formatGain(g.gradeGain)}</span>
                        </>
                      )
                      return (
                        <li key={g.chapterId}>
                          {own ? (
                            <Link
                              to={'/capitole/' + g.chapterId}
                              className="inline-flex rounded-full border px-3 py-1 text-sm hover:bg-muted"
                            >
                              {chip}
                            </Link>
                          ) : (
                            <span className="inline-flex rounded-full border px-3 py-1 text-sm">{chip}</span>
                          )}
                        </li>
                      )
                    })}
                  </ul>
                </div>
              )}
              <p className="text-xs text-muted-foreground">
                Estimare din {roCount(prediction.answers, 'răspuns', 'răspunsuri')}. Nu e o notă oficială.
              </p>
            </>
          )}
        </CardContent>
      </Card>
    </HomeSection>
  )
}
