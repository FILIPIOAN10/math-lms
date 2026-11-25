import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { ErrorState } from '@/components/ErrorState'
import { Card, CardContent } from '@/components/ui/card'
import { HomeEmpty } from '@/components/home/HomeBits'
import { ReviewStatusLine } from '@/components/ReviewStatusLine'
import {
  getChildAssignments,
  getChildAttempts,
  getChildEnMastery,
  getChildProgress,
  getChildReview,
  listMyChildren,
  type ChildDto,
  type EnChapterMastery,
  type ProgressPointDto,
  type ReviewStatus,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatDate, roCount } from '@/lib/format'
import { GradeFigure } from '@/components/GradeFigure'
import { latestGrade, todoAssignments, weakestChapter, weekSummary, type WeekSummary } from '@/lib/home'
import { LoadingCards } from '@/components/LoadingCards'

interface ChildOverview {
  child: ChildDto
  latest: ProgressPointDto | null
  awaiting: number
  /** Homework still to hand in, and how much of it is past its deadline. */
  homeworkToDo: number
  homeworkOverdue: number
  /** The daily review (E4); null when it could not be loaded. */
  review: ReviewStatus | null
  /** Homework handed in over the last seven days. */
  week: WeekSummary
  /** The EN chapter to revise first; null when none is weak (or the map could not be loaded). */
  weakest: EnChapterMastery | null
}

/** The child's week in one plain sentence each: what was handed in, and what to revise first. */
function WeekLine({ week, weakest }: { week: WeekSummary; weakest: EnChapterMastery | null }) {
  return (
    <div data-testid="child-week" className="space-y-1 rounded-lg bg-muted/60 px-3 py-2 text-sm">
      <p className="text-xs font-medium text-muted-foreground">Săptămâna aceasta</p>
      <p>
        {week.handedIn === 0
          ? 'Nicio temă predată în ultimele 7 zile.'
          : `${roCount(week.handedIn, 'temă predată', 'teme predate')}${week.late > 0 ? `, ${week.late} cu întârziere` : ', toate la timp'}.`}
      </p>
      {weakest && (
        <p>
          De recapitulat întâi: <span className="font-medium">{weakest.title}</span>
          <span className="text-muted-foreground"> ({weakest.percent}% corect)</span>
        </p>
      )}
    </div>
  )
}

/**
 * The parent's start page: one card per child with what is due, the week in plain words (homework handed in, the
 * chapter to revise first), the daily review, the latest grade and what waits for the teacher.
 */
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
            // The review and the chapter map are extras: the card still shows without them.
            const [review, mastery] = await Promise.all([
              getChildReview(child.id).catch(() => null),
              getChildEnMastery(child.id).catch(() => []),
            ])
            const todo = todoAssignments(homework)
            return {
              child,
              latest: latestGrade(progress),
              awaiting: attempts.filter((a) => a.mode === 'TEST' && a.status === 'SUBMITTED').length,
              homeworkToDo: todo.length,
              homeworkOverdue: todo.filter((a) => a.overdue).length,
              review,
              week: weekSummary(homework, attempts, Date.now()),
              weakest: weakestChapter(mastery),
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

  if (error) return <ErrorState message={error} />
  if (!overviews) return <LoadingCards />

  if (overviews.length === 0) {
    return (
      <HomeEmpty>
        Niciun elev nu este legat încă de contul tău. Cere profesorului să te lege de copilul tău.
      </HomeEmpty>
    )
  }

  return (
    <div className="space-y-3">
      {overviews.map(({ child, latest, awaiting, homeworkToDo, homeworkOverdue, review, week, weakest }) => (
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
            <ReviewStatusLine review={review} testId="child-review" />
            <WeekLine week={week} weakest={weakest} />
            <div className="grid gap-3 sm:grid-cols-[2fr_1fr]">
              <div>
                <p className="text-xs font-medium text-muted-foreground">Ultima notă</p>
                {latest ? (
                  <>
                    <GradeFigure score={latest.score} maxScore={latest.maxScore} size="md" />
                    <p className="truncate text-sm text-muted-foreground">
                      {latest.quizTitle} · {formatDate(latest.submittedAt)}
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
