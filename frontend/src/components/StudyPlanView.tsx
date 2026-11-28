import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { HomeSection } from '@/components/home/HomeBits'
import { EnGoalChart } from '@/components/EnGoalChart'
import { type StudyPlan, type StudyPlanWeek } from '@/lib/api'
import { formatGrade } from '@/lib/enPrediction'
import { chapterGroups, goalLine, itemLink, itemText, modeText, todayActions, weekDates, weekTitle } from '@/lib/studyPlan'

/** Beyond this many weeks the rest wait behind a button: a June exam seen from December is 28 weeks. */
const WEEKS_SHOWN = 4

/**
 * „Planul până la EN" as everyone sees it: where the student stands (and whether they are behind), what kind of plan
 * it is, the estimate day by day against the target, and the weeks up to the exam. The student's own view
 * ({@code own}) adds today's block and makes every step a link; the parent's and the teacher's only read it.
 */
export function StudyPlanView({ plan, own }: { plan: StudyPlan; own: boolean }) {
  const [allWeeks, setAllWeeks] = useState(false)
  const line = goalLine(plan.grade, plan.target, plan.daysLeft)
  const pinned = new Set(plan.pins.map((p) => p.chapterId))
  const weeks = allWeeks ? plan.weeks : plan.weeks.slice(0, WEEKS_SHOWN)

  return (
    <div className="space-y-4">
      <Card>
        <CardContent className="space-y-2 py-4">
          {line && <p data-testid="study-plan-goal-line" className="text-lg font-semibold">{line}</p>}
          {plan.behind && plan.expectedToday !== null && (
            <p data-testid="study-plan-behind" className="rounded-md bg-warning/15 px-3 py-2 text-sm text-warning-foreground">
              {own
                ? `Ești puțin în urmă față de ritmul spre țintă: azi ar fi bine să fii pe la ~${formatGrade(plan.expectedToday)}. Un capitol în plus săptămâna asta te aduce înapoi.`
                : `În urmă față de ritmul spre țintă: azi ar trebui să fie pe la ~${formatGrade(plan.expectedToday)}.`}
            </p>
          )}
          <p className="text-sm text-muted-foreground">{modeText(plan, own)}</p>
        </CardContent>
      </Card>

      {own && plan.weeks.length > 0 && (
        <HomeSection title="Azi">
          <Card data-testid="study-plan-today">
            <CardContent className="flex flex-wrap gap-2 py-4">
              {todayActions(plan).map((a, i) => (
                <Link key={a.to + a.label} to={a.to} className={buttonVariants({ variant: i === 0 ? 'default' : 'outline' })}>
                  {a.label}
                </Link>
              ))}
            </CardContent>
          </Card>
        </HomeSection>
      )}

      {plan.history.length > 0 && (
        <HomeSection title="Nota estimată, zi de zi">
          <Card>
            <CardContent className="py-4">
              <EnGoalChart history={plan.history} target={plan.target} />
            </CardContent>
          </Card>
        </HomeSection>
      )}

      {plan.weeks.length > 0 && (
        <HomeSection title="Săptămânile până la EN">
          <div className="space-y-3">
            {weeks.map((week) => (
              <WeekCard key={week.number} week={week} own={own} practiceQuestions={plan.practiceQuestions}
                        pinned={pinned} />
            ))}
            {plan.weeks.length > WEEKS_SHOWN && (
              <Button variant="ghost" size="sm" onClick={() => setAllWeeks((v) => !v)}>
                {allWeeks ? 'Arată mai puține' : `Arată toate cele ${plan.weeks.length} săptămâni`}
              </Button>
            )}
          </div>
        </HomeSection>
      )}
    </div>
  )
}

function WeekCard({ week, own, practiceQuestions, pinned }: {
  week: StudyPlanWeek
  own: boolean
  practiceQuestions: number
  pinned: Set<number>
}) {
  const onlyReview = !week.finalWeek && week.items.every((i) => i.type === 'REVIEW')
  return (
    <Card data-testid="study-plan-week">
      <CardContent className="space-y-3 py-4">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h3 className="font-medium">
            {weekTitle(week)} <span className="text-sm font-normal text-muted-foreground">· {weekDates(week)}</span>
          </h3>
          {week.finalWeek && <Badge variant="secondary">Simulări</Badge>}
        </div>
        {onlyReview && (
          <p className="text-sm text-muted-foreground">Niciun capitol nou: recapitularea zilnică ține ce ai lucrat.</p>
        )}
        {chapterGroups(week).map((group) => (
          <div key={group.chapterId ?? 'other'} className="space-y-1">
            {group.chapterId !== null && (
              <div className="flex flex-wrap items-center gap-2">
                <p className="text-sm font-medium">
                  <span className="font-semibold">{group.code}</span> {group.title}
                </p>
                {pinned.has(group.chapterId) && <Badge variant="outline">pus de profesor</Badge>}
              </div>
            )}
            <ul className="flex flex-wrap gap-2">
              {group.items.map((item, i) => (
                <li key={item.type + i}>
                  {own ? (
                    <Link to={itemLink(item)} className="inline-flex rounded-full border px-3 py-1 text-sm hover:bg-muted">
                      {itemText(item, practiceQuestions)}
                    </Link>
                  ) : (
                    <span className="inline-flex rounded-full border px-3 py-1 text-sm">{itemText(item, practiceQuestions)}</span>
                  )}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}
