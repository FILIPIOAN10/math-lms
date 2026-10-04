import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { AttemptStatusBadge } from '@/components/AttemptStatusBadge'
import { MathContent } from '@/components/MathContent'
import { getAttemptResult, type AttemptResultViewDto, type ItemResultDto } from '@/lib/api'
import { errorMessage } from '@/lib/errors'

function ItemResult({ item, index }: { item: ItemResultDto; index: number }) {
  const [showBarem, setShowBarem] = useState(false)
  // An unanswered grilă has no response row at all, so null there means 0 — only OPEN waits for the teacher.
  const points =
    item.type === 'OPEN' && item.awardedPoints === null
      ? 'în corectare'
      : `${item.awardedPoints ?? 0} / ${item.points} p`

  return (
    <Card>
      <CardContent className="space-y-3 py-4">
        <div className="flex items-center justify-between gap-2">
          <p className="text-sm font-medium">Subiectul {index + 1}</p>
          <span className="text-sm text-muted-foreground">{points}</span>
        </div>
        <MathContent>{item.statement}</MathContent>

        {item.type === 'SINGLE_CHOICE' ? (
          <div className="space-y-1 text-sm">
            <p className={item.correct ? 'text-emerald-700 dark:text-emerald-300' : 'text-destructive'}>
              {item.correct ? '✓ ' : '✗ '}Răspunsul tău:{' '}
              {item.selectedOptionText === null ? (
                <em>fără răspuns</em>
              ) : (
                <MathContent className="inline">{item.selectedOptionText}</MathContent>
              )}
            </p>
            {!item.correct && item.correctOptionText !== null && (
              <p>
                Răspuns corect: <MathContent className="inline">{item.correctOptionText}</MathContent>
              </p>
            )}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">
            {item.photoUploaded ? 'Ai trimis o poză cu rezolvarea.' : 'Nu ai trimis nicio poză pentru acest subiect.'}
          </p>
        )}

        {item.barem && (
          <div className="space-y-2">
            <Button size="xs" variant="outline" onClick={() => setShowBarem((v) => !v)}>
              {showBarem ? 'Ascunde baremul' : 'Arată baremul'}
            </Button>
            {showBarem && (
              <div className="rounded-lg bg-muted p-3 text-sm">
                <MathContent>{item.barem}</MathContent>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

/** The student's own result (Q8 backend): score, per-item right/wrong, and the barem after submit. */
export function AttemptResultPage() {
  const { attemptId } = useParams<{ attemptId: string }>()
  const [result, setResult] = useState<AttemptResultViewDto | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getAttemptResult(Number(attemptId))
      .then((r) => !cancelled && setResult(r))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [attemptId])

  const pointsSoFar = result?.items.reduce((sum, i) => sum + (i.awardedPoints ?? 0), 0) ?? 0

  return (
    <div className="min-h-screen bg-muted p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <div className="flex items-center justify-between">
          <Link to="/quizzes" className="text-sm text-muted-foreground hover:text-foreground hover:underline">
            ← Testele mele
          </Link>
          <Link to="/" className={buttonVariants({ variant: 'outline', size: 'sm' })}>Acasă</Link>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}
        {!result && !error && <p className="text-muted-foreground">Se încarcă...</p>}

        {result && (
          <>
            <Card>
              <CardContent className="space-y-2 py-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h1 className="text-xl font-semibold">{result.quizTitle}</h1>
                  <AttemptStatusBadge status={result.status} />
                </div>
                {result.status === 'GRADED' ? (
                  <p data-testid="result-score" className="text-3xl font-semibold">
                    {result.finalScore} <span className="text-base text-muted-foreground">/ {result.maxScore} puncte</span>
                  </p>
                ) : (
                  <p className="text-sm text-muted-foreground">
                    Grilele sunt corectate ({pointsSoFar} puncte până acum). Subiectele cu rezolvare
                    așteaptă corectura profesorului — revino mai târziu pentru nota finală.
                  </p>
                )}
              </CardContent>
            </Card>

            {result.items.map((item, index) => (
              <ItemResult key={item.position + '-' + index} item={item} index={index} />
            ))}
          </>
        )}
      </div>
    </div>
  )
}
