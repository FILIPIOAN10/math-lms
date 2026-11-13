import { useState, type ReactNode } from 'react'
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
  /** The teacher's hints, in order; revealed one at a time before the answer. Absent/empty = none. */
  hints?: string[]
}

/** What checking an answer gives back: the verdict, the right option, and what explains it. */
export interface PracticeCheck {
  correct: boolean
  correctOptionId: number | null
  mistake?: string | null
  solution?: string | null
}

/**
 * One grilă answered on its own: pick an option → "Verifică". Right → the verdict and the solution. Wrong → one more
 * try first (Khan Academy: find your own mistake before the answer is shown): the wrong pick stays crossed out, what it
 * gives away is shown as a nudge, and "Verifică din nou" checks the second pick here, against the answer the server
 * already sent - only the first answer counts (the review box, the chapter score). Then the full feedback. The teacher's
 * hints can be revealed one at a time until the answer is shown. Shared by the daily review and a chapter's practice,
 * which differ only in where the question comes from, what follows the feedback ({@code extraFeedback}) and the last
 * button. Keyed by the question in the parents, so a new question starts with a clean slate.
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
  hideSource = false,
  beforeCheck,
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
  /** Keep the test's title back until the answer is shown: a title like "Fracții" names the method to use. */
  hideSource?: boolean
  /** Shown above "Verifică" until the first answer is checked (the review's confidence tap). */
  beforeCheck?: ReactNode
  nextLabel: string
  onCheck: () => void
  onNext: () => void
}) {
  const [secondPick, setSecondPick] = useState<number | null>(null)
  const [secondChecked, setSecondChecked] = useState(false)
  const [gaveUp, setGaveUp] = useState(false)
  const [hintsShown, setHintsShown] = useState(0)

  const hints = question.hints ?? []
  // A wrong first answer whose right option we know: one more try before the answer is shown.
  const retrying = result !== null && !result.correct && result.correctOptionId !== null && !secondChecked && !gaveUp
  const revealed = result !== null && !retrying
  const finalPick = secondChecked ? secondPick : selected
  const secondRight = secondChecked && secondPick === result?.correctOptionId
  const shownPick = result !== null && !result.correct && result.correctOptionId !== null ? secondPick : selected

  return (
    <Card data-testid={`${name}-card`}>
      <CardContent className={cn('space-y-4 py-5', readingTextClass)}>
        <p className="text-xs font-medium text-muted-foreground">
          Întrebarea {position + 1} din {total}
        </p>
        {(!hideSource || revealed) && <p className="text-xs text-muted-foreground">{question.quizTitle}</p>}
        <MathContent>{question.statement}</MathContent>
        <QuestionImage itemId={question.itemId} image={question.image} />

        <div className="space-y-2">
          {question.options.map((option, optionIndex) => {
            const isRight = revealed && result?.correctOptionId === option.id
            const firstWrong = result !== null && !result.correct && selected === option.id
            const isWrongPick = revealed && !isRight && (finalPick === option.id || firstWrong)
            const locked = revealed || busy || (retrying && firstWrong)
            return (
              <label
                key={option.id}
                className={`${optionRowClass} ${locked ? '' : 'cursor-pointer'} ${
                  isRight ? 'border-emerald-500 bg-emerald-500/10'
                    : isWrongPick || (retrying && firstWrong) ? 'border-destructive bg-destructive/10'
                      : shownPick === option.id ? 'border-primary bg-primary/5' : 'border-border hover:bg-muted'
                }`}
              >
                <input
                  type="radio"
                  name={`${name}-${question.itemId}`}
                  className={optionRadioClass}
                  checked={shownPick === option.id}
                  disabled={locked}
                  onChange={() => (retrying ? setSecondPick(option.id) : onSelect(option.id))}
                />
                <OptionLetter index={optionIndex} />
                <MathContent inline large>{option.text}</MathContent>
                {isRight && (
                  <span className="ml-auto font-semibold text-emerald-700 dark:text-emerald-300">
                    <span aria-hidden="true">✓</span>
                    <span className="sr-only"> (răspunsul corect)</span>
                  </span>
                )}
                {(isWrongPick || (retrying && firstWrong)) && (
                  <span className="ml-auto font-semibold text-destructive">
                    <span aria-hidden="true">✗</span>
                    <span className="sr-only"> (alegerea ta, greșită)</span>
                  </span>
                )}
              </label>
            )
          })}
        </div>

        {!revealed && hints.length > 0 && (
          <div data-testid={`${name}-hints`} className="space-y-2">
            {hints.slice(0, hintsShown).map((hint, i) => (
              <div key={i} className="rounded-lg border border-sky-500/40 bg-sky-500/10 p-3 text-sm">
                <p className="text-xs font-medium text-sky-800 dark:text-sky-200">Indiciul {i + 1}</p>
                <MathContent>{hint}</MathContent>
              </div>
            ))}
            {hintsShown < hints.length && (
              <Button size="sm" variant="outline" onClick={() => setHintsShown((n) => n + 1)}>
                {hints.length === 1 ? 'Arată un indiciu' : `Arată indiciul ${hintsShown + 1} din ${hints.length}`}
              </Button>
            )}
          </div>
        )}

        {retrying && result && (
          <div data-testid={`${name}-retry`} role="status" className="space-y-2 rounded-lg bg-muted p-3 text-sm">
            <p className="font-medium text-destructive">
              ✗ Nu e corect. Mai încearcă o dată — prima încercare s-a socotit deja.
            </p>
            {result.mistake && <MistakeNote text={result.mistake} />}
          </div>
        )}

        {revealed && result && (
          <div data-testid={`${name}-feedback`} role="status" className="space-y-2 rounded-lg bg-muted p-3 text-sm">
            <p className={`font-medium ${result.correct || secondRight ? 'text-emerald-700 dark:text-emerald-300' : 'text-destructive'}`}>
              {result.correct ? '✓ Corect!'
                : secondRight ? '✓ Corect, din a doua încercare! Data viitoare o știi din prima.'
                  : '✗ Greșit — răspunsul corect e marcat cu ✓.'}
            </p>
            {result.mistake && !secondChecked && !gaveUp && <MistakeNote text={result.mistake} />}
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
        {result === null && beforeCheck}

        <div className="flex flex-wrap justify-end gap-2">
          {revealed ? (
            <Button onClick={onNext}>{nextLabel}</Button>
          ) : retrying ? (
            <>
              <Button variant="outline" onClick={() => setGaveUp(true)}>Arată răspunsul</Button>
              <Button onClick={() => setSecondChecked(true)} disabled={secondPick === null}>Verifică din nou</Button>
            </>
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
