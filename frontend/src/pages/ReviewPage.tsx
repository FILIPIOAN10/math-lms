import { useCallback, useEffect, useState } from 'react'
import { Flame } from 'lucide-react'
import { Link } from 'react-router-dom'
import { EmptyArt } from '@/components/EmptyArt'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { RevisionNote } from '@/components/RevisionNote'
import { PageHeader } from '@/components/PageHeader'
import { PracticeQuestionCard } from '@/components/PracticeQuestionCard'
import {
  answerReview,
  answerReviewExtra,
  getReviewExtra,
  getReviewToday,
  type ReviewQuestion,
  type ReviewResult,
  type ReviewToday,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { roCount } from '@/lib/format'
import { LoadingCards } from '@/components/LoadingCards'

/** "12 oct." from the server's "2026-10-12" (a calendar day, not an instant: no time-zone shift). */
function shortDay(isoDay: string): string {
  const [y, m, d] = isoDay.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('ro-RO', { day: 'numeric', month: 'short' })
}

function whereNext(result: ReviewResult): string {
  return `Revine pe ${shortDay(result.nextDueOn)}`
}

/**
 * The daily review (E4, Leitner boxes): one question at a time from today's queue - the multiple-choice questions the
 * student answered before, those answered wrong coming back sooner. Check → the verdict, the explanation and which box
 * the question moves to → next. At most 10 a day; when they are done, "Gata pe azi" - and "Încă 5" for whoever wants to
 * keep going: more of their own questions, the weakest first, checked but not stored (no box moves, the plan stays).
 */
export function ReviewPage() {
  const [today, setToday] = useState<ReviewToday | null>(null)
  // "Încă 5": the extra batch being answered (null = today's queue), the ids already shown, none left to show.
  const [extra, setExtra] = useState<ReviewQuestion[] | null>(null)
  const [seen, setSeen] = useState<number[]>([])
  const [exhausted, setExhausted] = useState(false)
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
      setExtra(null)
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

  const questions = extra ?? today?.questions ?? []
  const question = questions[index] ?? null
  const isLast = index === questions.length - 1

  async function check() {
    if (!question || selected === null) return
    setBusy(true)
    setError(null)
    try {
      setResult(await (extra ? answerReviewExtra : answerReview)(question.itemId, selected))
    } catch (e) {
      setError(errorMessage(e))
      void load() // the queue changed under us (another tab, a new day): start again from the server's view
    } finally {
      setBusy(false)
    }
  }

  async function loadExtra() {
    setBusy(true)
    setError(null)
    try {
      const batch = await getReviewExtra(seen)
      if (batch.length === 0) {
        setExhausted(true)
        return
      }
      setSeen((s) => [...s, ...batch.map((q) => q.itemId)])
      setExtra(batch)
      setIndex(0)
      setSelected(null)
      setResult(null)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  function next() {
    if (isLast) {
      if (extra) {
        setExtra([]) // back to "Gata pe azi", with "Încă 5" for the next batch
        setIndex(0)
        setResult(null)
        return
      }
      void load() // the fresh counts: done today, the streak
      return
    }
    setIndex((i) => i + 1)
    setSelected(null)
    setResult(null)
    setError(null)
  }

  const answeredHere = today && !extra ? index + (result ? 1 : 0) : 0
  const total = today ? today.doneToday + today.remainingToday : 0
  const finished = today !== null && questions.length === 0

  return (
    <div className="p-4">
      <div className="mx-auto max-w-4xl space-y-4">
        <PageHeader
          title="Recapitulare"
          description="Câte puțin în fiecare zi: întrebările greșite revin a doua zi, cele știute tot mai rar (după 3, 7, 14, 30 de zile)."
        />
        {loadError && <p className="text-sm text-destructive">{loadError}</p>}
        {!today && !loadError && <LoadingCards />}

        {today && (
          <p data-testid="review-stats" className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
            <span>
              Azi: <span className="font-medium text-foreground">{today.doneToday + answeredHere} / {total}</span>
            </span>
            <span>
              Seria: <span className="font-medium text-foreground">{roCount(today.streak, 'zi', 'zile')}</span>
              {today.streak > 0 && <Flame aria-hidden="true" className="ml-1 inline size-3.5 text-warm" fill="currentColor" />}
            </span>
            <span>
              Stăpânite: <span className="font-medium text-foreground">{today.mastered} din {today.totalCards}</span>
            </span>
          </p>
        )}

        {finished && today && (
          <Card>
            <CardContent className="flex flex-col items-center space-y-3 py-6 text-center">
              <EmptyArt symbol={today.totalCards === 0 ? '↻' : '✓'} />
              {today.totalCards === 0 ? (
                <p className="text-muted-foreground">
                  Încă nu ai nimic de recapitulat. Întrebările apar aici după ce exersezi sau predai un test.
                </p>
              ) : today.doneToday > 0 ? (
                <p className="text-lg font-medium">Gata pe azi! 🎉 Revino mâine pentru următoarele.</p>
              ) : (
                <p className="text-muted-foreground">Nimic de recapitulat azi. Ești la zi!</p>
              )}
              {today.totalCards > 0 && !exhausted && (
                <div data-testid="review-extra" className="space-y-1">
                  <Button onClick={() => void loadExtra()} disabled={busy}>
                    {seen.length === 0 ? 'Încă 5 întrebări' : 'Încă 5'}
                  </Button>
                  <p className="text-xs text-muted-foreground">
                    Din întrebările tale, cele mai slabe întâi. Exersare în plus: nu schimbă când revin în recapitulare.
                  </p>
                </div>
              )}
              {exhausted && (
                <p data-testid="review-extra-none" className="text-sm text-muted-foreground">
                  Ai trecut prin toate întrebările tale. Revino mâine!
                </p>
              )}
              {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
              <Link to="/quizzes" className={buttonVariants({ variant: 'outline', size: 'sm' })}>Testele mele</Link>
            </CardContent>
          </Card>
        )}

        {today && question && (
          <PracticeQuestionCard
            key={question.itemId}
            name="review"
            position={index}
            total={questions.length}
            question={question}
            selected={selected}
            onSelect={setSelected}
            result={result}
            busy={busy}
            error={error}
            hideSource
            extraFeedback={result && (
              <>
                {result.enChapter?.ready && <RevisionNote chapter={result.enChapter} newTab />}
                <p className="text-xs text-muted-foreground">
                  {extra ? 'Exersare în plus: întrebarea revine în recapitulare tot când era programată.' : whereNext(result)}
                </p>
              </>
            )}
            nextLabel={isLast ? 'Termină' : 'Următoarea'}
            onCheck={check}
            onNext={next}
          />
        )}
      </div>
    </div>
  )
}
