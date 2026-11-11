import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { EmptyArt } from '@/components/EmptyArt'
import { ErrorState } from '@/components/ErrorState'
import { LoadingCards } from '@/components/LoadingCards'
import { PageHeader } from '@/components/PageHeader'
import { PracticeQuestionCard } from '@/components/PracticeQuestionCard'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { answerChapterPractice, getChapterPractice, type ChapterPractice, type ChapterPracticeResult } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { usePageTitle } from '@/lib/usePageTitle'

/**
 * Practising one EN chapter, from its page: up to five grile linked to it, the ones the student got wrong last time
 * first, each checked at once with the solution - the "practice this unit" step after reading the theory. Only
 * questions whose answers the student may already see (handed-in tests, tests open for practice); nothing is graded.
 */
export function ChapterPracticePage() {
  const chapterId = Number(useParams<{ id: string }>().id)
  const [practice, setPractice] = useState<ChapterPractice | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [index, setIndex] = useState(0)
  const [selected, setSelected] = useState<number | null>(null)
  const [result, setResult] = useState<ChapterPracticeResult | null>(null)
  const [right, setRight] = useState(0)
  const [finished, setFinished] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  usePageTitle(practice ? `Exersează ${practice.code}` : 'Exersează capitolul')

  // "Încă o dată" asks the server again: the ones answered wrong this round may come first next time.
  const [round, setRound] = useState(0)

  useEffect(() => {
    let cancelled = false
    getChapterPractice(chapterId)
      .then((p) => !cancelled && setPractice(p))
      .catch((e) => !cancelled && setLoadError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [chapterId, round])

  function again() {
    setPractice(null)
    setLoadError(null)
    setIndex(0)
    setSelected(null)
    setResult(null)
    setRight(0)
    setFinished(false)
    setRound((r) => r + 1)
  }

  const question = practice && !finished ? practice.questions[index] ?? null : null
  const isLast = practice !== null && index === practice.questions.length - 1
  const chapterHref = `/capitole/${chapterId}`

  async function check() {
    if (!question || selected === null) return
    setBusy(true)
    setError(null)
    try {
      const checked = await answerChapterPractice(chapterId, question.itemId, selected)
      setResult(checked)
      if (checked.correct) setRight((r) => r + 1)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  function next() {
    if (isLast) {
      setFinished(true)
      return
    }
    setIndex((i) => i + 1)
    setSelected(null)
    setResult(null)
    setError(null)
  }

  const title = practice ? `Exersează: ${practice.code} · ${practice.title}` : 'Exersează capitolul'

  return (
    <div className="p-4">
      <div className="mx-auto max-w-4xl space-y-4">
        <PageHeader
          title={title}
          description="Grile din testele tale despre capitolul acesta, întâi cele greșite. Nu se dă notă."
          breadcrumbs={[{ label: 'Capitole EN', to: '/capitole' }, { label: practice?.code ?? 'Capitol', to: chapterHref }, { label: 'Exersează' }]}
        />
        {loadError && <ErrorState message={loadError} />}
        {!practice && !loadError && <LoadingCards />}

        {practice && practice.questions.length === 0 && (
          <Card data-testid="practice-empty">
            <CardContent className="flex flex-col items-center space-y-3 py-6 text-center">
              <EmptyArt symbol="?" />
              <p className="text-muted-foreground">
                Încă nu sunt grile din capitolul acesta pe care să le poți exersa. Apar aici după ce predai un test cu
                întrebări din el, sau când profesorul deschide un test pentru exersare.
              </p>
              <Link to={chapterHref} className={buttonVariants({ variant: 'outline', size: 'sm' })}>
                Înapoi la capitol
              </Link>
            </CardContent>
          </Card>
        )}

        {practice && finished && (
          <Card data-testid="practice-done">
            <CardContent className="flex flex-col items-center space-y-3 py-6 text-center">
              <p className="text-lg font-medium">
                Ai răspuns corect la {right} din {practice.questions.length}.
              </p>
              <p className="text-sm text-muted-foreground">
                {right === practice.questions.length
                  ? 'Foarte bine! Capitolul pare stăpânit.'
                  : 'Recitește teoria și modelele rezolvate, apoi încearcă din nou: întâi vin cele greșite.'}
              </p>
              <div className="flex flex-wrap justify-center gap-2">
                <Button onClick={again}>Încă o dată</Button>
                <Link to={chapterHref} className={buttonVariants({ variant: 'outline' })}>
                  Înapoi la capitol
                </Link>
              </div>
            </CardContent>
          </Card>
        )}

        {question && practice && (
          <PracticeQuestionCard
            key={question.itemId}
            name="practice"
            position={index}
            total={practice.questions.length}
            question={question}
            selected={selected}
            onSelect={setSelected}
            result={result}
            busy={busy}
            error={error}
            nextLabel={isLast ? 'Vezi cum ai mers' : 'Următoarea'}
            onCheck={check}
            onNext={next}
          />
        )}
      </div>
    </div>
  )
}
