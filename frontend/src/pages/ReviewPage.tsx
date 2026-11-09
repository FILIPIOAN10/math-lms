import { useCallback, useEffect, useState } from 'react'
import { Flame } from 'lucide-react'
import { Link } from 'react-router-dom'
import { EmptyArt } from '@/components/EmptyArt'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { RevisionNote } from '@/components/RevisionNote'
import { PageHeader } from '@/components/PageHeader'
import { PracticeQuestionCard } from '@/components/PracticeQuestionCard'
import { answerReview, getReviewToday, type ReviewResult, type ReviewToday } from '@/lib/api'
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

        {today && today.questions.length === 0 && (
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
              <Link to="/quizzes" className={buttonVariants({ variant: 'outline', size: 'sm' })}>Testele mele</Link>
            </CardContent>
          </Card>
        )}

        {today && question && (
          <PracticeQuestionCard
            name="review"
            position={index}
            total={today.questions.length}
            question={question}
            selected={selected}
            onSelect={setSelected}
            result={result}
            busy={busy}
            error={error}
            extraFeedback={result && (
              <>
                {result.enChapter?.ready && <RevisionNote chapter={result.enChapter} newTab />}
                <p className="text-xs text-muted-foreground">{whereNext(result)}</p>
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
