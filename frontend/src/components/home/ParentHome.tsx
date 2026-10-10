import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { HomeEmpty } from '@/components/home/HomeBits'
import {
  getChildAssignments,
  getChildAttempts,
  getChildProgress,
  listMyChildren,
  type ChildDto,
  type ProgressPointDto,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatDate, roCount } from '@/lib/format'
import { formatGrade, romanianGrade } from '@/lib/grades'
import { latestGrade, todoAssignments } from '@/lib/home'

interface ChildOverview {
  child: ChildDto
  latest: ProgressPointDto | null
  awaiting: number
  /** Homework still to hand in, and how much of it is past its deadline. */
  homeworkToDo: number
  homeworkOverdue: number
}

/** The parent's start page: one card per child with the latest grade and what waits for the teacher. */
export function ParentHome() {
  const [overviews, setOverviews] = useState<ChildOverview[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listMyChildren()
      .then((children) =>
        Promise.all(
          children.map(async (child) => {
            const [attempts, progress, homework] = await Promise.all([
              getChildAttempts(child.id),
              getChildProgress(child.id),
              getChildAssignments(child.id),
            ])
            const todo = todoAssignments(homework)
            return {
              child,
              latest: latestGrade(progress),
              awaiting: attempts.filter((a) => a.mode === 'TEST' && a.status === 'SUBMITTED').length,
              homeworkToDo: todo.length,
              homeworkOverdue: todo.filter((a) => a.overdue).length,
            }
          }),
        ),
      )
      .then((list) => !cancelled && setOverviews(list))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [])

  if (error) return <p className="text-sm text-destructive">{error}</p>
  if (!overviews) return <p className="text-muted-foreground">Se încarcă...</p>

  if (overviews.length === 0) {
    return (
      <HomeEmpty>
        Niciun elev nu este legat încă de contul tău. Cere profesorului să te lege de copilul tău.
      </HomeEmpty>
    )
  }

  return (
    <div className="space-y-3">
      {overviews.map(({ child, latest, awaiting, homeworkToDo, homeworkOverdue }) => (
        <Card key={child.id} data-testid="child-card">
          <CardContent className="space-y-3 py-4">
            <div className="flex items-center justify-between gap-3">
              <p className="truncate text-lg font-medium">{child.fullName}</p>
              <Link to={`/parent/children/${child.id}`} className={buttonVariants({ variant: 'outline', size: 'sm' })}>
                Vezi detalii
              </Link>
            </div>
            <p data-testid="child-homework" className="text-sm">
              {homeworkToDo === 0 ? (
                <span className="text-muted-foreground">Nicio temă de predat.</span>
              ) : (
                <>
                  <span className="font-medium">{roCount(homeworkToDo, 'temă', 'teme')} de predat</span>
                  {homeworkOverdue > 0 && (
                    <span className="font-medium text-destructive"> · {homeworkOverdue} cu termenul depășit</span>
                  )}
                </>
              )}
            </p>
            <div className="grid gap-3 sm:grid-cols-[2fr_1fr]">
              <div>
                <p className="text-xs font-medium text-muted-foreground">Ultima notă</p>
                {latest ? (
                  <>
                    <p className="text-2xl font-semibold">
                      {latest.score} <span className="text-base text-muted-foreground">/ {latest.maxScore} puncte</span>
                      {romanianGrade(latest.score, latest.maxScore) !== null && (
                        <span className="ml-2 text-base font-medium text-muted-foreground">
                          · nota {formatGrade(romanianGrade(latest.score, latest.maxScore)!)}
                        </span>
                      )}
                    </p>
                    <p className="truncate text-sm text-muted-foreground">
                      {latest.quizTitle} · {latest.percent}% · {formatDate(latest.submittedAt)}
                    </p>
                  </>
                ) : (
                  <p className="text-sm text-muted-foreground">Nicio notă încă.</p>
                )}
              </div>
              <div>
                <p className="text-xs font-medium text-muted-foreground">În corectare</p>
                <p className="text-sm">
                  {awaiting === 0 ? 'Nimic nu așteaptă.' : `${roCount(awaiting, 'test', 'teste')} în corectare`}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
