import { type EnSnapshot } from '@/lib/api'
import { formatGrade } from '@/lib/enPrediction'
import { useMeasuredWidth } from '@/lib/useMeasuredWidth'

const HEIGHT = 240
const PAD = { top: 18, right: 16, bottom: 32, left: 30 }
/** The grade bands the app uses everywhere (sub 5, 5 – 6,99, 7 – 8,99, 9 – 10). */
const GRADE_GRID = [1, 5, 7, 9, 10]
const DAY_MONTH = new Intl.DateTimeFormat('ro-RO', { day: 'numeric', month: 'short', timeZone: 'UTC' })

const dayOf = (iso: string) => new Date(iso + 'T00:00:00Z').getTime()
const formatDay = (iso: string) => DAY_MONTH.format(new Date(iso + 'T00:00:00Z'))

/**
 * „Nota estimată, zi de zi": the estimate kept each day (a line, its range as a band) against the target (a dashed
 * line), on the 1 - 10 scale with the lines of the grade bands, the days spaced as they fell. Plain SVG like
 * {@link ProgressChart}, drawn at the width it gets; the numbers are repeated in a table for screen readers.
 */
export function EnGoalChart({ history, target }: { history: EnSnapshot[]; target: number | null }) {
  const [measure, width] = useMeasuredWidth()
  if (history.length === 0) {
    return (
      <p data-testid="en-goal-chart-empty" className="text-sm text-muted-foreground">
        Graficul apare din prima zi cu notă estimată.
      </p>
    )
  }

  const innerW = width - PAD.left - PAD.right
  const innerH = HEIGHT - PAD.top - PAD.bottom
  const first = dayOf(history[0].day)
  const span = dayOf(history[history.length - 1].day) - first
  const x = (iso: string) => PAD.left + (span === 0 ? innerW / 2 : ((dayOf(iso) - first) / span) * innerW)
  const y = (grade: number) => PAD.top + innerH - ((grade - 1) / 9) * innerH
  const line = history.map((s, i) => `${i === 0 ? 'M' : 'L'} ${x(s.day)} ${y(s.grade)}`).join(' ')
  const band = [...history.map((s) => `${x(s.day)},${y(s.high)}`), ...[...history].reverse().map((s) => `${x(s.day)},${y(s.low)}`)]
    .join(' ')
  const last = history[history.length - 1]
  const summary = `Nota estimată: ${formatGrade(history[0].grade)} pe ${formatDay(history[0].day)}, ` +
    `${formatGrade(last.grade)} pe ${formatDay(last.day)}` + (target === null ? '' : `; ținta ${formatGrade(target)}`)

  return (
    <div ref={measure} className="space-y-2" data-testid="en-goal-chart">
      <svg viewBox={`0 0 ${width} ${HEIGHT}`} role="img" aria-label={summary} className="w-full">
        {GRADE_GRID.map((grade) => (
          <g key={grade}>
            <line x1={PAD.left} x2={width - PAD.right} y1={y(grade)} y2={y(grade)} className="stroke-border" strokeWidth={1} />
            <text x={PAD.left - 8} y={y(grade)} textAnchor="end" dominantBaseline="middle" className="fill-muted-foreground text-xs">
              {grade}
            </text>
          </g>
        ))}
        {target !== null && (
          <g data-testid="en-goal-chart-target">
            <line x1={PAD.left} x2={width - PAD.right} y1={y(target)} y2={y(target)} className="stroke-foreground"
                  strokeWidth={1.5} strokeDasharray="6 4" />
            <text x={width - PAD.right} y={target > 9.4 ? y(target) + 14 : y(target) - 6} textAnchor="end"
                  className="fill-foreground text-xs font-medium">
              {'Ținta ' + formatGrade(target)}
            </text>
          </g>
        )}
        {history.length > 1 && <polygon points={band} className="fill-primary/15" />}
        <path d={line} fill="none" className="stroke-primary" strokeWidth={2.5} strokeLinejoin="round" />
        {history.map((s) => (
          <circle key={s.day} data-testid="en-goal-chart-point" cx={x(s.day)} cy={y(s.grade)} r={4}
                  className="fill-primary stroke-background" strokeWidth={2}>
            <title>{`${formatDay(s.day)}: ~${formatGrade(s.grade)} (${formatGrade(s.low)} - ${formatGrade(s.high)})`}</title>
          </circle>
        ))}
        <text x={x(history[0].day)} y={HEIGHT - 8} textAnchor={history.length === 1 ? 'middle' : 'start'}
              className="fill-muted-foreground text-xs">
          {formatDay(history[0].day)}
        </text>
        {history.length > 1 && (
          <text x={x(last.day)} y={HEIGHT - 8} textAnchor="end" className="fill-muted-foreground text-xs">
            {formatDay(last.day)}
          </text>
        )}
      </svg>
      <table className="sr-only">
        <caption>Nota estimată pe zile</caption>
        <thead>
          <tr>
            <th>Ziua</th>
            <th>Nota estimată</th>
          </tr>
        </thead>
        <tbody>
          {history.map((s) => (
            <tr key={s.day}>
              <td>{formatDay(s.day)}</td>
              <td>{`~${formatGrade(s.grade)} (${formatGrade(s.low)} - ${formatGrade(s.high)})`}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
