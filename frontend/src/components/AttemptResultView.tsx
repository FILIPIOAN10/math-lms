import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { AttemptStatusBadge } from '@/components/AttemptStatusBadge'
import { MathContent } from '@/components/MathContent'
import { type AttemptResultViewDto, type ItemResultDto } from '@/lib/api'

/** Who is reading: only the wording changes ("Răspunsul tău" vs "Răspunsul elevului"). */
export type ResultAudience = 'student' | 'parent'

function ItemResult({ item, index, audience }: { item: ItemResultDto; index: number; audience: ResultAudience }) {
  const [showBarem, setShowBarem] = useState(false)
  const own = audience === 'student'
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
              {item.correct ? '✓ ' : '✗ '}
              {own ? 'Răspunsul tău:' : 'Răspunsul elevului:'}{' '}
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
            {item.photoUploaded
              ? own
                ? 'Ai trimis o poză cu rezolvarea.'
                : 'Elevul a trimis o poză cu rezolvarea.'
              : own
                ? 'Nu ai trimis nicio poză pentru acest subiect.'
                : 'Elevul nu a trimis nicio poză pentru acest subiect.'}
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

/**
 * The result of one attempt: score (or "waiting for the teacher"), then every item with right/wrong
 * and the barem. Shared by the student's own result page and the parent's view of a child's result.
 */
export function AttemptResultView({ result, audience = 'student' }: { result: AttemptResultViewDto; audience?: ResultAudience }) {
  const pointsSoFar = result.items.reduce((sum, i) => sum + (i.awardedPoints ?? 0), 0)

  return (
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
        <ItemResult key={item.position + '-' + index} item={item} index={index} audience={audience} />
      ))}
    </>
  )
}
