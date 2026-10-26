import { formatGrade, romanianGrade } from '@/lib/grades'
import { cn } from '@/lib/utils'

/**
 * A graded test at a glance: the 1–10 grade ("nota") is the big number - it is what a student and a parent look for -
 * and the points with the percent sit under it. A quiz worth no points has no grade, so only its points are shown.
 * `size` md is for a figure sharing a card with other things (a parent's card per child).
 */
export function GradeFigure({
  score,
  maxScore,
  size = 'lg',
  testId,
}: {
  score: number
  maxScore: number
  size?: 'lg' | 'md'
  /** The figure's data-testid; the grade itself is `${testId}-grade`. */
  testId?: string
}) {
  const grade = romanianGrade(score, maxScore)
  const points = `${score} / ${maxScore} puncte`
  if (grade === null) {
    return <p data-testid={testId} className="text-2xl font-semibold tabular-nums">{points}</p>
  }
  return (
    <div data-testid={testId}>
      <p className="flex items-baseline gap-2">
        <span className="text-sm font-medium text-muted-foreground">Nota</span>
        <span
          data-testid={testId && `${testId}-grade`}
          className={cn('font-semibold tabular-nums', size === 'lg' ? 'text-4xl' : 'text-3xl')}
        >
          {formatGrade(grade)}
        </span>
      </p>
      <p className="text-sm text-muted-foreground">
        {points} · {Math.round((100 * score) / maxScore)}%
      </p>
    </div>
  )
}
