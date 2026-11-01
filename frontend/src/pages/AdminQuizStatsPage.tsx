import { Fragment, useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { ErrorState } from '@/components/ErrorState'
import { MathContent } from '@/components/MathContent'
import { SectionHeadingView } from '@/components/SectionHeadingView'
import { numberItems } from '@/lib/sections'
import {
  correctAnswerKey, correctAnswerNumber, getAiImportSettings, getQuizStats, type NumberStat, type OptionStat, type QuizItemStat,
  type QuizStatsDto,
} from '@/lib/api'
import { itemTypeLabel } from '@/lib/numericAnswer'
import { errorMessage } from '@/lib/errors'
import { PageHeader } from '@/components/PageHeader'
import { LoadingCards } from '@/components/LoadingCards'
import { MistakeEditor } from '@/components/MistakeEditor'
import { useConfirm } from '@/components/useConfirm'
import { commonMistakes } from '@/lib/quizStats'
import { roCount } from '@/lib/format'
import { formatGrade, formatPoints, GRADE_FILL, GRADE_TEXT, gradeBand, romanianGrade, type GradeBand } from '@/lib/grades'
import { cn } from '@/lib/utils'

/** A horizontal bar: `fraction` (0..1) of the track is filled, in `fill` (the brand colour by default). */
function Bar({ fraction, fill = 'bg-primary' }: { fraction: number; fill?: string }) {
  return (
    <div className="h-2 w-full rounded-full bg-muted" aria-hidden="true">
      <div className={cn('h-2 rounded-full', fill)} style={{ width: `${Math.round(Math.max(0, Math.min(1, fraction)) * 100)}%` }} />
    </div>
  )
}

/** The colour of each grade group the server sends: under 5, 5 – 6,99, 7 – 8,99, 9 – 10. */
const BUCKET_BANDS: GradeBand[] = ['low', 'mid', 'high', 'high']

/** What each option of a grilă drew, so the teacher sees which mistake the class made, not just how many went wrong. */
function OptionCounts({ item, graded, aiOn, onMakeCorrect, onMistakeSaved }: {
  item: QuizItemStat
  graded: number
  aiOn: boolean
  onMakeCorrect: (option: OptionStat) => void
  onMistakeSaved: () => void
}) {
  const blank = Math.max(0, graded - item.options.reduce((sum, o) => sum + o.chosen, 0))
  return (
    <ul className="space-y-2" data-testid="stats-options">
      {item.options.map((option) => (
        <li key={option.id} className="space-y-1 text-sm" data-testid="stats-option">
          <div className="flex flex-wrap items-baseline justify-between gap-x-3">
            <span className="min-w-0">
              {option.letter}) <MathContent inline>{option.text}</MathContent>
              {option.correct && (
                <span className="ml-1 font-semibold text-emerald-700 dark:text-emerald-300">
                  <span aria-hidden="true">✓</span>
                  <span className="sr-only"> (răspunsul marcat corect)</span>
                </span>
              )}
            </span>
            <span className="shrink-0 tabular-nums text-muted-foreground">{roCount(option.chosen, 'elev', 'elevi')}</span>
          </div>
          <Bar fraction={graded === 0 ? 0 : option.chosen / graded} fill={GRADE_FILL[option.correct ? 'high' : 'low']} />
          {!option.correct && (
            <div className="flex flex-wrap items-start gap-1">
              <MistakeEditor option={option} aiOn={aiOn} onSaved={onMistakeSaved} />
              <Button type="button" size="xs" variant="ghost" onClick={() => onMakeCorrect(option)}>
                Asta e varianta corectă
              </Button>
            </div>
          )}
        </li>
      ))}
      {blank > 0 && (
        <li className="text-sm text-muted-foreground" data-testid="stats-blank">Fără răspuns: {roCount(blank, 'elev', 'elevi')}</li>
      )}
    </ul>
  )
}

/** The numbers typed for a numeric question, the same value counted once however it was written. */
function NumberCounts({ item, graded, onMakeCorrect }: {
  item: QuizItemStat
  graded: number
  onMakeCorrect: (number: NumberStat) => void
}) {
  const blank = Math.max(0, graded - item.numbers.reduce((sum, n) => sum + n.chosen, 0))
  return (
    <div className="space-y-2" data-testid="stats-numbers">
      <p className="text-sm">Răspuns corect: <span className="font-medium">{item.numericAnswer}</span></p>
      <ul className="space-y-2">
        {item.numbers.map((number) => (
          <li key={number.value} className="space-y-1 text-sm" data-testid="stats-number">
            <div className="flex flex-wrap items-baseline justify-between gap-x-3">
              <span className="min-w-0">
                {number.value}
                {number.correct && (
                  <span className="ml-1 font-semibold text-emerald-700 dark:text-emerald-300">
                    <span aria-hidden="true">✓</span>
                    <span className="sr-only"> (răspunsul marcat corect)</span>
                  </span>
                )}
              </span>
              <span className="shrink-0 tabular-nums text-muted-foreground">{roCount(number.chosen, 'elev', 'elevi')}</span>
            </div>
            <Bar fraction={graded === 0 ? 0 : number.chosen / graded} fill={GRADE_FILL[number.correct ? 'high' : 'low']} />
            {!number.correct && (
              <Button type="button" size="xs" variant="ghost" onClick={() => onMakeCorrect(number)}>
                Asta e varianta corectă
              </Button>
            )}
          </li>
        ))}
        {blank > 0 && <li className="text-sm text-muted-foreground">Altceva sau fără răspuns: {roCount(blank, 'elev', 'elevi')}</li>}
      </ul>
    </div>
  )
}

/** Teacher analytics for one quiz: average, score distribution, and which items were hardest. */
export function AdminQuizStatsPage() {
  const { quizId } = useParams<{ quizId: string }>()
  const [stats, setStats] = useState<QuizStatsDto | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const { confirm, dialog: confirmDialog } = useConfirm()
  const [aiOn, setAiOn] = useState(false) // "Propune cu AI" only where the server has the AI switched on

  const load = useCallback(() => getQuizStats(Number(quizId)), [quizId])

  useEffect(() => {
    getAiImportSettings()
      .then((s) => setAiOn(s.enabled))
      .catch(() => undefined) // the button just stays hidden
  }, [])

  useEffect(() => {
    let cancelled = false
    load()
      .then((s) => !cancelled && setStats(s))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [load])

  /** A taken question keeps its answer everywhere else: this is the one place that changes it, and it says what follows. */
  async function makeCorrect(label: string, newAnswer: string, correct: () => Promise<{ changedGrades: number }>) {
    const yes = await confirm({
      title: `Schimbi răspunsul corect la întrebarea ${label}?`,
      body: `${newAnswer} devine răspunsul corect și toate lucrările se notează din nou. Elevii cărora li se schimbă nota o văd ca notă nouă.`,
      confirmLabel: 'Schimbă și recalculează',
    })
    if (!yes) return
    setError(null)
    setNotice(null)
    try {
      const { changedGrades } = await correct()
      setStats(await load())
      setNotice(`✓ Răspunsul corect e acum ${newAnswer}. ${changedGrades === 0
        ? 'Nicio notă nu s-a schimbat.' : `S-au schimbat ${roCount(changedGrades, 'notă', 'note')}.`}`)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  const maxBucket = stats ? Math.max(1, ...stats.distribution.map((b) => b.count)) : 1
  // The hardest single-choice item = the lowest correct rate (only meaningful once something is graded).
  const rated = stats?.items.filter((i) => i.correctRate !== null) ?? []
  const hardest = rated.length > 1 ? rated.reduce((a, b) => ((b.correctRate ?? 1) < (a.correctRate ?? 1) ? b : a)) : null
  const averageGrade = stats?.averageScore != null ? romanianGrade(stats.averageScore, stats.maxScore) : null
  const numbered = stats ? numberItems(stats.items) : []
  const suspects = numbered.filter(({ item }) => item.suspect !== null)
  const common = stats ? commonMistakes(numbered, stats.gradedAttempts) : []

  return (
    <div className="p-4">
      {confirmDialog}
      <div className="mx-auto max-w-3xl space-y-4">
        <PageHeader
          title={stats?.title ?? 'Statistici'}
          description={stats ? 'Din lucrările notate: fiecare elev o dată, cu cea mai mare notă.' : undefined}
          documentTitle={stats ? `Statistici · ${stats.title}` : 'Statistici'}
          breadcrumbs={[
            { label: 'Teste', to: '/admin/quizzes' },
            ...(stats ? [{ label: stats.title, to: `/admin/quizzes/${quizId}` }] : []),
            { label: 'Statistici' },
          ]}
        />

        {error && <ErrorState message={error} />}
        {!stats && !error && <LoadingCards />}
        {notice && <p role="status" data-testid="stats-notice" className="text-sm text-emerald-700 dark:text-emerald-300">{notice}</p>}

        {stats && (
          <>
            {suspects.length > 0 && (
              <p role="status" data-testid="stats-suspects"
                 className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-sm">
                {suspects.length === 1 ? 'De verificat întrebarea ' : 'De verificat întrebările '}
                {suspects.map((s) => s.label).join(', ')}: elevii cei mai buni au ales altă variantă decât cea marcată corectă.
              </p>
            )}

            {common.length > 0 && (
              <Card>
                <CardContent className="space-y-2 py-4" data-testid="stats-common">
                  <h2 className="font-medium">Greșeli frecvente</h2>
                  <ul className="space-y-1 text-sm">
                    {common.map(({ label, option }) => (
                      <li key={option.id}>
                        <span className="font-medium">Întrebarea {label}, varianta {option.letter}):</span>{' '}
                        {roCount(option.chosen, 'elev', 'elevi')}
                        {option.mistake
                          ? <> · <MathContent inline>{option.mistake}</MathContent></>
                          : <span className="text-muted-foreground"> · fără explicație încă</span>}
                      </li>
                    ))}
                  </ul>
                </CardContent>
              </Card>
            )}

            {stats.gradedAttempts === 0 ? (
              <p className="text-sm text-muted-foreground" data-testid="stats-empty">
                Niciun elev nu a fost notat încă la acest test. Statisticile apar după prima notă finală.
              </p>
            ) : (
              <Card>
                <CardContent className="flex flex-wrap gap-8 py-4">
                  <div>
                    <p className="text-sm text-muted-foreground">Elevi cu notă</p>
                    <p className="text-2xl font-semibold" data-testid="stats-graded">{stats.gradedAttempts}</p>
                  </div>
                  <div>
                    <p className="text-sm text-muted-foreground">Media</p>
                    {averageGrade !== null && (
                      <p className={cn('text-2xl font-semibold', GRADE_TEXT[gradeBand(averageGrade)])} data-testid="stats-average">
                        nota {formatGrade(averageGrade)}
                      </p>
                    )}
                    <p className="text-sm text-muted-foreground" data-testid="stats-average-points">
                      {stats.averageScore !== null && formatPoints(stats.averageScore)} / {stats.maxScore} p
                      {stats.averagePercent !== null && ` · ${stats.averagePercent}%`}
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
                    <Bar fraction={bucket.count / maxBucket} fill={GRADE_FILL[BUCKET_BANDS[i]]} />
                    <span className="text-right">{bucket.count}</span>
                  </div>
                ))}
              </CardContent>
            </Card>

            <section className="space-y-2">
              <h2 className="font-medium">Pe întrebări</h2>
              {numbered.map(({ item, label, heading }) => (
                <Fragment key={item.itemId}>
                {heading && <SectionHeadingView heading={heading} />}
                <Card>
                  <CardContent className="space-y-2 py-3" data-testid="stats-item">
                    <div className="flex items-center justify-between gap-2 text-sm">
                      <span className="font-medium">
                        Întrebarea {label} · {itemTypeLabel(item.type)} · {item.points} p
                      </span>
                      {hardest?.itemId === item.itemId && (
                        <span className="rounded bg-rose-500/15 px-1.5 py-0.5 text-xs text-rose-700 dark:text-rose-300">
                          cel mai greu
                        </span>
                      )}
                    </div>
                    <MathContent className="text-sm">{item.statement}</MathContent>
                    {item.suspect && (
                      <p data-testid="stats-suspect" className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-2 text-sm">
                        Verifică răspunsul marcat: {item.suspect.chosen} din {item.suspect.strong} elevi buni au ales{' '}
                        varianta {item.suspect.letter}). Dacă ei au dreptate, apasă „Asta e varianta corectă” la {item.suspect.letter}).
                      </p>
                    )}
                    {item.correctRate !== null && (
                      <div className="grid grid-cols-[1fr_3rem] items-center gap-3 text-sm">
                        <Bar fraction={item.correctRate} />
                        <span className="text-right">{Math.round(item.correctRate * 100)}%</span>
                      </div>
                    )}
                    {item.options.length > 0 && stats.gradedAttempts > 0 && (
                      <OptionCounts
                        item={item}
                        graded={stats.gradedAttempts}
                        aiOn={aiOn}
                        onMakeCorrect={(option) => void makeCorrect(label, `${option.letter})`, () => correctAnswerKey(item.itemId, option.id))}
                        onMistakeSaved={() => void load().then(setStats).catch((e) => setError(errorMessage(e)))}
                      />
                    )}
                    {item.type === 'NUMERIC' && stats.gradedAttempts > 0 && (
                      <NumberCounts
                        item={item}
                        graded={stats.gradedAttempts}
                        onMakeCorrect={(number) => void makeCorrect(label, number.value, () => correctAnswerNumber(item.itemId, number.value))}
                      />
                    )}
                    {item.averagePoints !== null && (
                      <p className="text-xs text-muted-foreground">
                        {item.correctRate !== null ? 'răspuns corect · ' : ''}
                        în medie {formatPoints(item.averagePoints)} / {item.points} p
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
