import { Fragment, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Card, CardContent } from '@/components/ui/card'
import { MathContent } from '@/components/MathContent'
import { SectionHeadingView } from '@/components/SectionHeadingView'
import { numberItems } from '@/lib/sections'
import { getQuizStats, type QuizStatsDto } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { PageHeader } from '@/components/PageHeader'
import { LoadingCards } from '@/components/LoadingCards'
import { GRADE_FILL, gradeBand, romanianGrade } from '@/lib/grades'
import { cn } from '@/lib/utils'

/** A horizontal bar: `fraction` (0..1) of the track is filled, in `fill` (the brand colour by default). */
function Bar({ fraction, fill = 'bg-primary' }: { fraction: number; fill?: string }) {
  return (
    <div className="h-2 w-full rounded-full bg-muted" aria-hidden="true">
      <div className={cn('h-2 rounded-full', fill)} style={{ width: `${Math.round(Math.max(0, Math.min(1, fraction)) * 100)}%` }} />
    </div>
  )
}

/** The grade band of a distribution bucket (equal slices of 0–100%), taken at the middle of its slice. */
function bucketBand(index: number, buckets: number) {
  return gradeBand(romanianGrade(index + 0.5, buckets)!)
}

/** Teacher analytics for one quiz: average, score distribution, and which items were hardest. */
export function AdminQuizStatsPage() {
  const { quizId } = useParams<{ quizId: string }>()
  const [stats, setStats] = useState<QuizStatsDto | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getQuizStats(Number(quizId))
      .then((s) => !cancelled && setStats(s))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [quizId])

  const maxBucket = stats ? Math.max(1, ...stats.distribution.map((b) => b.count)) : 1
  // The hardest single-choice item = the lowest correct rate (only meaningful once something is graded).
  const rated = stats?.items.filter((i) => i.correctRate !== null) ?? []
  const hardest = rated.length > 1 ? rated.reduce((a, b) => ((b.correctRate ?? 1) < (a.correctRate ?? 1) ? b : a)) : null

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <PageHeader
          title={stats?.title ?? 'Statistici'}
          description={stats ? 'Statisticile testului, din lucrările notate.' : undefined}
          documentTitle={stats ? `Statistici · ${stats.title}` : 'Statistici'}
          breadcrumbs={[
            { label: 'Teste', to: '/admin/quizzes' },
            ...(stats ? [{ label: stats.title, to: `/admin/quizzes/${quizId}` }] : []),
            { label: 'Statistici' },
          ]}
        />

        {error && <p className="text-sm text-destructive">{error}</p>}
        {!stats && !error && <LoadingCards />}

        {stats && (
          <>

            {stats.gradedAttempts === 0 ? (
              <p className="text-sm text-muted-foreground" data-testid="stats-empty">
                Niciun elev nu a fost notat încă la acest test. Statisticile apar după prima notă finală.
              </p>
            ) : (
              <Card>
                <CardContent className="flex flex-wrap gap-8 py-4">
                  <div>
                    <p className="text-sm text-muted-foreground">Lucrări notate</p>
                    <p className="text-2xl font-semibold" data-testid="stats-graded">{stats.gradedAttempts}</p>
                  </div>
                  <div>
                    <p className="text-sm text-muted-foreground">Media</p>
                    <p className="text-2xl font-semibold" data-testid="stats-average">
                      {stats.averageScore?.toFixed(1)} <span className="text-base text-muted-foreground">/ {stats.maxScore} p</span>
                      {stats.averagePercent !== null && (
                        <span className="ml-2 text-base text-muted-foreground">({stats.averagePercent}%)</span>
                      )}
                    </p>
                  </div>
                </CardContent>
              </Card>
            )}

            <Card>
              <CardContent className="space-y-3 py-4">
                <h2 className="font-medium">Distribuția notelor</h2>
                {stats.distribution.map((bucket, i) => (
                  <div key={bucket.label} className="grid grid-cols-[5rem_1fr_2rem] items-center gap-3 text-sm">
                    <span className="text-muted-foreground">{bucket.label}</span>
                    <Bar fraction={bucket.count / maxBucket} fill={GRADE_FILL[bucketBand(i, stats.distribution.length)]} />
                    <span className="text-right">{bucket.count}</span>
                  </div>
                ))}
              </CardContent>
            </Card>

            <section className="space-y-2">
              <h2 className="font-medium">Pe întrebări</h2>
              {numberItems(stats.items).map(({ item, label, heading }) => (
                <Fragment key={item.itemId}>
                {heading && <SectionHeadingView heading={heading} />}
                <Card>
                  <CardContent className="space-y-2 py-3" data-testid="stats-item">
                    <div className="flex items-center justify-between gap-2 text-sm">
                      <span className="font-medium">
                        Întrebarea {label} · {item.type === 'SINGLE_CHOICE' ? 'Grilă' : 'Cu rezolvare'} · {item.points} p
                      </span>
                      {hardest?.itemId === item.itemId && (
                        <span className="rounded bg-rose-500/15 px-1.5 py-0.5 text-xs text-rose-700 dark:text-rose-300">
                          cel mai greu
                        </span>
                      )}
                    </div>
                    <MathContent className="text-sm">{item.statement}</MathContent>
                    {item.correctRate !== null && (
                      <div className="grid grid-cols-[1fr_3rem] items-center gap-3 text-sm">
                        <Bar fraction={item.correctRate} />
                        <span className="text-right">{Math.round(item.correctRate * 100)}%</span>
                      </div>
                    )}
                    {item.averagePoints !== null && (
                      <p className="text-xs text-muted-foreground">
                        {item.correctRate !== null ? 'răspuns corect · ' : ''}
                        în medie {item.averagePoints.toFixed(1)} / {item.points} p
                      </p>
                    )}
                  </CardContent>
                </Card>
                </Fragment>
              ))}
            </section>
          </>
        )}
      </div>
    </div>
  )
}
