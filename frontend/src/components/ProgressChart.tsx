import { type ProgressPointDto } from '@/lib/api'
import { formatDate } from '@/lib/format'

const WIDTH = 640
const HEIGHT = 260
const PAD = { top: 16, right: 20, bottom: 36, left: 44 }
const GRID = [0, 25, 50, 75, 100]

/**
 * Percent-over-time line chart of graded attempts (oldest → newest, evenly spaced). Plain SVG on theme
 * colors, no chart library: one data series, so a line + points + a 0–100 % grid is all it needs. The
 * data is repeated as a table below for screen readers and for exact numbers.
 */
export function ProgressChart({ points }: { points: ProgressPointDto[] }) {
  if (points.length === 0) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="progress-empty">
        Nu există încă teste notate. Progresul apare după prima notă finală.
      </p>
    )
  }

  const innerW = WIDTH - PAD.left - PAD.right
  const innerH = HEIGHT - PAD.top - PAD.bottom
  const x = (i: number) => PAD.left + (points.length === 1 ? innerW / 2 : (i * innerW) / (points.length - 1))
  const y = (percent: number) => PAD.top + innerH - (percent / 100) * innerH
  const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${x(i)} ${y(p.percent)}`).join(' ')
  const average = Math.round(points.reduce((sum, p) => sum + p.percent, 0) / points.length)
  const summary = `Progres: ${points.length} teste notate, media ${average}%, ultimul ${points[points.length - 1].percent}%`

  return (
    <div className="space-y-3" data-testid="progress-chart">
      <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="img" aria-label={summary} className="w-full">
        {GRID.map((g) => (
          <g key={g}>
            <line x1={PAD.left} x2={WIDTH - PAD.right} y1={y(g)} y2={y(g)} className="stroke-border" strokeWidth={1} />
            <text x={PAD.left - 8} y={y(g)} textAnchor="end" dominantBaseline="middle"
                  className="fill-muted-foreground text-[11px]">
              {g}%
            </text>
          </g>
        ))}
        <path d={path} fill="none" className="stroke-primary" strokeWidth={2.5} strokeLinejoin="round" />
        {points.map((p, i) => (
          <circle key={p.attemptId} cx={x(i)} cy={y(p.percent)} r={5} className="fill-primary stroke-background" strokeWidth={2}>
            <title>{`${p.quizTitle} — ${p.score}/${p.maxScore} (${p.percent}%) · ${formatDate(p.submittedAt)}`}</title>
          </circle>
        ))}
        <text x={x(0)} y={HEIGHT - 10} textAnchor={points.length === 1 ? 'middle' : 'start'}
              className="fill-muted-foreground text-[11px]">
          {formatDate(points[0].submittedAt)}
        </text>
        {points.length > 1 && (
          <text x={x(points.length - 1)} y={HEIGHT - 10} textAnchor="end" className="fill-muted-foreground text-[11px]">
            {formatDate(points[points.length - 1].submittedAt)}
          </text>
        )}
      </svg>

      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-muted-foreground">
            <th className="py-1 font-medium">Test</th>
            <th className="py-1 font-medium">Data</th>
            <th className="py-1 text-right font-medium">Nota</th>
          </tr>
        </thead>
        <tbody>
          {points.map((p) => (
            <tr key={p.attemptId} className="border-t border-border" data-testid="progress-row">
              <td className="py-1">{p.quizTitle}</td>
              <td className="py-1 text-muted-foreground">{formatDate(p.submittedAt)}</td>
              <td className="py-1 text-right">
                {p.score} / {p.maxScore} <span className="text-muted-foreground">({p.percent}%)</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
