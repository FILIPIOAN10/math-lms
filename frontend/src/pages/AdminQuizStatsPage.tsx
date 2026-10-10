import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Card, CardContent } from '@/components/ui/card'
import { MathContent } from '@/components/MathContent'
import { getQuizStats, type QuizStatsDto } from '@/lib/api'
import { errorMessage } from '@/lib/errors'

/** A horizontal bar: `fraction` (0..1) of the track is filled. Plain divs on theme colors. */
function Bar({ fraction }: { fraction: number }) {
  return (
    <div className="h-2 w-full rounded-full bg-muted" aria-hidden="true">
      <div className="h-2 rounded-full bg-primary" style={{ width: `${Math.round(Math.max(0, Math.min(1, fraction)) * 100)}%` }} />
    </div>
  )
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
        <div className="flex items-center justify-between">
          <Link to="/admin/quizzes" className="text-sm text-muted-foreground hover:text-foreground hover:underline">
            ← Quiz-uri
          </Link>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}
        {!stats && !error && <p className="text-muted-foreground">Se încarcă...</p>}

        {stats && (
          <>
            <h1 className="text-2xl font-semibold">{stats.title}</h1>

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
                {stats.distribution.map((bucket) => (
                  <div key={bucket.label} className="grid grid-cols-[5rem_1fr_2rem] items-center gap-3 text-sm">
                    <span className="text-muted-foreground">{bucket.label}</span>
                    <Bar fraction={bucket.count / maxBucket} />
                    <span className="text-right">{bucket.count}</span>
                  </div>
                ))}
              </CardContent>
            </Card>

            <section className="space-y-2">
              <h2 className="font-medium">Pe subiecte</h2>
              {stats.items.map((item, index) => (
                <Card key={item.itemId}>
                  <CardContent className="space-y-2 py-3" data-testid="stats-item">
                    <div className="flex items-center justify-between gap-2 text-sm">
                      <span className="font-medium">
                        Subiectul {index + 1} · {item.type === 'SINGLE_CHOICE' ? 'Grilă' : 'Deschis'} · {item.points} p
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
              ))}
            </section>
          </>
        )}
      </div>
    </div>
  )
}
