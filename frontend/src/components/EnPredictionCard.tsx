import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { HomeSection } from '@/components/home/HomeBits'
import {
  getChildEnGoal,
  getChildEnPrediction,
  getMyEnGoal,
  getMyEnPrediction,
  getStudentEnGoal,
  getStudentEnPrediction,
  type EnGoalSummary,
  type EnPrediction,
} from '@/lib/api'
import { barPercent, formatGain, formatRange } from '@/lib/enPrediction'
import { roCount } from '@/lib/format'
import { goalLine } from '@/lib/studyPlan'

/**
 * „Ce notă iei la EN?": the estimated EN grade as a range on a 1 - 10 bar, and the chapters that would raise it most.
 * It loads its own data, so each page only says whose estimate to show: the student's own (no childId or
 * studentId; the chapters then link to their lessons and the empty state offers the daily review), a parent's child
 * (childId) or, for the teacher, any student (studentId). A failed load shows nothing.
 *
 * With a target („Ținta la EN"), a line says where the student is, where they aim and how long is left, and a tick
 * marks the target on the bar. The student's own card links to their plan (or asks them to pick a target); someone
 * else's links only where the page gives `planTo`. A target that cannot be read leaves the estimate as it was.
 */
export function EnPredictionCard({ childId, studentId, planTo }: { childId?: number; studentId?: number; planTo?: string }) {
  const [prediction, setPrediction] = useState<EnPrediction | null>(null)
  const [goal, setGoal] = useState<EnGoalSummary | null>(null)
  const own = childId === undefined && studentId === undefined
  const planLink = own ? '/plan' : planTo

  useEffect(() => {
    let cancelled = false
    const load = childId !== undefined
      ? getChildEnPrediction(childId)
      : studentId !== undefined ? getStudentEnPrediction(studentId) : getMyEnPrediction()
    const loadGoal = childId !== undefined
      ? getChildEnGoal(childId)
      : studentId !== undefined ? getStudentEnGoal(studentId) : getMyEnGoal()
    load
      .then((p) => !cancelled && setPrediction(p))
      .catch(() => undefined)
    loadGoal
      .then((g) => !cancelled && setGoal(g))
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [childId, studentId])

  if (!prediction) return null

  const target = goal?.target ?? null
  const line = goal ? goalLine(prediction.available ? prediction.grade : null, target, goal.daysLeft) : null
  const goalRow = (line || (own && goal)) && (
    <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
      {line ? (
        <p data-testid="en-goal-line" className="font-medium">{line}</p>
      ) : (
        <p className="text-muted-foreground">Ce notă vrei la EN?</p>
      )}
      {planLink && (target !== null || own) && (
        <Link to={planLink} className={buttonVariants({ size: 'sm', variant: target === null ? 'default' : 'outline' })}>
          {target === null ? 'Alege-ți ținta' : 'Vezi planul'}
        </Link>
      )}
    </div>
  )

  return (
    <HomeSection title="Nota estimată la EN">
      <Card data-testid="en-prediction">
        <CardContent className="space-y-4 py-4">
          {!prediction.available || prediction.low === null || prediction.high === null ? (
            <>
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
              {goalRow}
            </>
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
                {target !== null && (
                  <div
                    data-testid="en-target-marker"
                    aria-hidden="true"
                    title={'Ținta'}
                    className="absolute -inset-y-1 w-0.5 -translate-x-1/2 rounded-full bg-foreground"
                    style={{ left: barPercent(target) + '%' }}
                  />
                )}
              </div>
              <div aria-hidden="true" className="flex justify-between text-xs text-muted-foreground">
                <span>1</span>
                <span>10</span>
              </div>
              {goalRow}
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
