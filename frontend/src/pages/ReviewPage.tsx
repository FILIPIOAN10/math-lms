import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { MathContent } from '@/components/MathContent'
import { QuestionImage } from '@/components/QuestionImage'
import { PageHeader } from '@/components/PageHeader'
import { answerReview, getReviewToday, type ReviewResult, type ReviewToday } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { roCount } from '@/lib/format'

const BOXES = 5

/** "12 oct." from the server's "2026-10-12" (a calendar day, not an instant: no time-zone shift). */
function shortDay(isoDay: string): string {
  const [y, m, d] = isoDay.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('ro-RO', { day: 'numeric', month: 'short' })
}

function whereNext(result: ReviewResult): string {
  const move = result.boxAfter > result.boxBefore
    ? `Urcă în cutia ${result.boxAfter}`
    : result.boxAfter < result.boxBefore
      ? `Coboară în cutia ${result.boxAfter}`
      : `Rămâne în cutia ${result.boxAfter}`
  return `${move} · revine pe ${shortDay(result.nextDueOn)}`
}

/**
 * The daily review (E4, Leitner boxes): one question at a time from today's queue - the multiple-choice questions the
 * student answered before, those answered wrong coming back sooner. Check → the verdict, the explanation and which box
 * the question moves to → next. At most 10 a day; when they are done, "Gata pe azi".
 */
export function ReviewPage() {
  const [today, setToday] = useState<ReviewToday | null>(null)
  const [index, setIndex] = useState(0)
  const [selected, setSelected] = useState<number | null>(null)
  const [result, setResult] = useState<ReviewResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const fresh = await getReviewToday()
      setToday(fresh)
      setIndex(0)
      setSelected(null)
      setResult(null)
    } catch (e) {
      setLoadError(errorMessage(e))
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    getReviewToday()
      .then((t) => !cancelled && setToday(t))
      .catch((e) => !cancelled && setLoadError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [])

  const question = today?.questions[index] ?? null
  const isLast = today !== null && index === today.questions.length - 1

  async function check() {
    if (!question || selected === null) return
    setBusy(true)
    setError(null)
    try {
      setResult(await answerReview(question.itemId, selected))
    } catch (e) {
      setError(errorMessage(e))
      void load() // the queue changed under us (another tab, a new day): start again from the server's view
    } finally {
      setBusy(false)
    }
  }

  function next() {
    if (isLast) {
      void load() // the fresh counts: done today, the streak
      return
    }
    setIndex((i) => i + 1)
    setSelected(null)
    setResult(null)
    setError(null)
  }

  const answeredHere = today ? index + (result ? 1 : 0) : 0
  const total = today ? today.doneToday + today.remainingToday : 0

  return (
    <div className="p-4">
      <div className="mx-auto max-w-2xl space-y-4">
        <PageHeader
          title="Recapitulare"
          description="Câte puțin în fiecare zi: întrebările greșite revin a doua zi, cele știute tot mai rar (după 3, 7, 14, 30 de zile)."
        />
        {loadError && <p className="text-sm text-destructive">{loadError}</p>}
        {!today && !loadError && <p className="text-muted-foreground">Se încarcă...</p>}

        {today && (
          <p data-testid="review-stats" className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
            <span>
              Azi: <span className="font-medium text-foreground">{today.doneToday + answeredHere} / {total}</span>
            </span>
            <span>
              Seria: <span className="font-medium text-foreground">{roCount(today.streak, 'zi', 'zile')}</span>
              {today.streak > 0 && ' 🔥'}
            </span>
            <span>
              Stăpânite: <span className="font-medium text-foreground">{today.mastered} din {today.totalCards}</span>
            </span>
          </p>
        )}

        {today && today.questions.length === 0 && (
          <Card>
            <CardContent className="space-y-3 py-6 text-center">
              {today.totalCards === 0 ? (
                <p className="text-muted-foreground">
                  Încă nu ai nimic de recapitulat. Întrebările apar aici după ce exersezi sau predai un test.
                </p>
              ) : today.doneToday > 0 ? (
                <p className="text-lg font-medium">Gata pe azi! 🎉 Revino mâine pentru următoarele.</p>
              ) : (
                <p className="text-muted-foreground">Nimic de recapitulat azi. Ești la zi!</p>
              )}
              <Link to="/quizzes" className={buttonVariants({ variant: 'outline', size: 'sm' })}>Testele mele</Link>
            </CardContent>
          </Card>
        )}

        {today && question && (
          <Card data-testid="review-card">
            <CardContent className="space-y-4 py-5">
              <p className="text-xs font-medium text-muted-foreground">
                Întrebarea {index + 1} din {today.questions.length} · cutia {question.box} din {BOXES}
              </p>
              <p className="text-xs text-muted-foreground">{question.quizTitle}</p>
              <MathContent>{question.statement}</MathContent>
              <QuestionImage itemId={question.itemId} image={question.image} />

              <div className="space-y-2">
                {question.options.map((option) => {
                  const isRight = result?.correctOptionId === option.id
                  const isWrongPick = result !== null && !result.correct && selected === option.id
                  return (
                    <label
                      key={option.id}
                      className={`flex cursor-pointer items-center gap-3 rounded-lg border p-2 text-sm ${
                        isRight ? 'border-emerald-500 bg-emerald-500/10'
                          : isWrongPick ? 'border-destructive bg-destructive/10'
                            : selected === option.id ? 'border-primary bg-primary/5' : 'border-border hover:bg-muted'
                      }`}
                    >
                      <input
                        type="radio"
                        name={`review-${question.itemId}`}
                        className="size-4 shrink-0"
                        checked={selected === option.id}
                        disabled={result !== null || busy}
                        onChange={() => setSelected(option.id)}
                      />
                      <MathContent inline>{option.text}</MathContent>
                      {isRight && (
                        <span className="ml-auto font-semibold text-emerald-700 dark:text-emerald-300">
                          <span aria-hidden="true">✓</span>
                          <span className="sr-only"> (răspunsul corect)</span>
                        </span>
                      )}
                      {isWrongPick && (
                        <span className="ml-auto font-semibold text-destructive">
                          <span aria-hidden="true">✗</span>
                          <span className="sr-only"> (alegerea ta, greșită)</span>
                        </span>
                      )}
                    </label>
                  )
                })}
              </div>

              {result && (
                <div data-testid="review-feedback" className="space-y-2 rounded-lg bg-muted p-3 text-sm">
                  <p className={`font-medium ${result.correct ? 'text-emerald-700 dark:text-emerald-300' : 'text-destructive'}`}>
                    {result.correct ? '✓ Corect!' : '✗ Greșit — răspunsul corect e marcat cu ✓.'}
                  </p>
                  {result.solution && (
                    <div>
                      <p className="text-xs font-medium text-muted-foreground">Rezolvare</p>
                      <MathContent>{result.solution}</MathContent>
                    </div>
                  )}
                  <p className="text-xs text-muted-foreground">{whereNext(result)}</p>
                </div>
              )}
              {error && <p role="alert" className="text-sm text-destructive">{error}</p>}

              <div className="flex justify-end">
                {result ? (
                  <Button onClick={next}>{isLast ? 'Termină' : 'Următoarea'}</Button>
                ) : (
                  <Button onClick={check} disabled={selected === null || busy}>
                    {busy ? 'Se verifică...' : 'Verifică'}
                  </Button>
                )}
              </div>
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  )
}
