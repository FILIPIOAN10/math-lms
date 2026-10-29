import { formatGrade, GRADE_STROKE, GRADE_TEXT, gradeBand, romanianGrade, type GradeBand } from '@/lib/grades'
import { cn } from '@/lib/utils'

/** The share of points as a ring, in the grade's colour. Decoration only: the percent is written next to it. */
function GradeRing({ percent, band, size }: { percent: number; band: GradeBand; size: 'lg' | 'md' }) {
  // r = 100 / 2π, so the circle is 100 long and the dash is simply the percent.
  return (
    <svg viewBox="0 0 36 36" aria-hidden="true" className={cn('shrink-0 -rotate-90', size === 'lg' ? 'size-14' : 'size-11')}>
      <circle cx="18" cy="18" r="15.9155" fill="none" strokeWidth="3.5" className="stroke-muted" />
      <circle
        cx="18" cy="18" r="15.9155" fill="none" strokeWidth="3.5" strokeLinecap="round"
        strokeDasharray={`${Math.max(percent, 1)} 100`}
        className={GRADE_STROKE[band]}
      />
    </svg>
  )
}

/**
 * A graded test at a glance: the 1–10 grade ("nota") is the big number - it is what a student and a parent look for -
 * coloured by how it went (red under 5, amber up to 8, green from 8), with a ring for the share of points and the
 * points with the percent under it. A quiz worth no points has no grade, so only its points are shown.
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
  const percent = Math.round((100 * score) / maxScore)
  const band = gradeBand(grade)
  return (
    <div data-testid={testId} className="flex items-center gap-3">
      <GradeRing percent={percent} band={band} size={size} />
      <div>
        <p className="flex items-baseline gap-2">
          <span className="text-sm font-medium text-muted-foreground">Nota</span>
          <span
            data-testid={testId && `${testId}-grade`}
            data-band={band}
            className={cn('font-semibold tabular-nums', GRADE_TEXT[band], size === 'lg' ? 'text-4xl' : 'text-3xl')}
          >
            {formatGrade(grade)}
          </span>
        </p>
        <p className="text-sm text-muted-foreground">
          {points} · {percent}%
        </p>
      </div>
    </div>
  )
}
