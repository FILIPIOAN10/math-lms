import type { ReactNode } from 'react'
import { MathContent } from '@/components/MathContent'
import { MistakeNote } from '@/components/MistakeNote'
import { OptionLetter } from '@/components/OptionLetter'
import { QuestionImage } from '@/components/QuestionImage'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { optionRadioClass, optionRowClass, readingTextClass } from '@/lib/formClasses'
import { cn } from '@/lib/utils'

/** The question as both the daily review and a chapter's practice send it. */
export interface PracticeQuestion {
  itemId: number
  quizTitle: string
  statement: string
  image?: string | null
  options: { id: number; text: string }[]
}

/** What checking an answer gives back: the verdict, the right option, and what explains it. */
export interface PracticeCheck {
  correct: boolean
  correctOptionId: number | null
  mistake?: string | null
  solution?: string | null
}

/**
 * One grilă answered on its own, checked at once: pick an option → "Verifică" → the verdict, the right option marked,
 * what the wrong pick gives away and the solution → next. Shared by the daily review and a chapter's practice, which
 * differ only in where the question comes from, what follows the feedback ({@code extraFeedback}) and the last button.
 */
export function PracticeQuestionCard({
  name,
  position,
  total,
  question,
  selected,
  onSelect,
  result,
  busy,
  error,
  extraFeedback,
  nextLabel,
  onCheck,
  onNext,
}: {
  /** "review" / "practice": the card's test ids and the radio group's name. */
  name: string
  /** 0-based. */
  position: number
  total: number
  question: PracticeQuestion
  selected: number | null
  onSelect: (optionId: number) => void
  result: PracticeCheck | null
  busy: boolean
  error: string | null
  extraFeedback?: ReactNode
  nextLabel: string
  onCheck: () => void
  onNext: () => void
}) {
  return (
    <Card data-testid={`${name}-card`}>
      <CardContent className={cn('space-y-4 py-5', readingTextClass)}>
        <p className="text-xs font-medium text-muted-foreground">
          Întrebarea {position + 1} din {total}
        </p>
        <p className="text-xs text-muted-foreground">{question.quizTitle}</p>
        <MathContent>{question.statement}</MathContent>
        <QuestionImage itemId={question.itemId} image={question.image} />

        <div className="space-y-2">
          {question.options.map((option, optionIndex) => {
            const isRight = result?.correctOptionId === option.id
            const isWrongPick = result !== null && !result.correct && selected === option.id
            return (
              <label
                key={option.id}
                className={`${optionRowClass} cursor-pointer ${
                  isRight ? 'border-emerald-500 bg-emerald-500/10'
                    : isWrongPick ? 'border-destructive bg-destructive/10'
                      : selected === option.id ? 'border-primary bg-primary/5' : 'border-border hover:bg-muted'
                }`}
              >
                <input
                  type="radio"
                  name={`${name}-${question.itemId}`}
                  className={optionRadioClass}
                  checked={selected === option.id}
                  disabled={result !== null || busy}
                  onChange={() => onSelect(option.id)}
                />
                <OptionLetter index={optionIndex} />
                <MathContent inline large>{option.text}</MathContent>
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
          <div data-testid={`${name}-feedback`} className="space-y-2 rounded-lg bg-muted p-3 text-sm">
            <p className={`font-medium ${result.correct ? 'text-emerald-700 dark:text-emerald-300' : 'text-destructive'}`}>
              {result.correct ? '✓ Corect!' : '✗ Greșit — răspunsul corect e marcat cu ✓.'}
            </p>
            {result.mistake && <MistakeNote text={result.mistake} />}
            {result.solution && (
              <div>
                <p className="text-xs font-medium text-muted-foreground">Rezolvare</p>
                <MathContent>{result.solution}</MathContent>
              </div>
            )}
            {extraFeedback}
          </div>
        )}
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}

        <div className="flex justify-end">
          {result ? (
            <Button onClick={onNext}>{nextLabel}</Button>
          ) : (
            <Button onClick={onCheck} disabled={selected === null || busy}>
              {busy ? 'Se verifică...' : 'Verifică'}
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
