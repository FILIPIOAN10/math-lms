import { useCallback, useState } from 'react'
import { type ProgressPointDto } from '@/lib/api'
import { formatDate, formatDay, roCount } from '@/lib/format'
import { formatGrade, romanianGrade } from '@/lib/grades'

/** "8,80", or "—" for a quiz worth no points. */
const gradeOf = (p: ProgressPointDto) => {
  const grade = romanianGrade(p.score, p.maxScore)
  return grade === null ? '—' : formatGrade(grade)
}

/** Before the first measure, and where nothing can be measured (tests). */
const DEFAULT_WIDTH = 640
/** Narrower than this the two dates under the line would collide: below it the drawing shrinks instead. */
const MIN_WIDTH = 280
const HEIGHT = 260
const PAD = { top: 18, right: 16, bottom: 32, left: 30 }
/** The lines of the grade bands the app uses everywhere (sub 5, 5 – 6,99, 7 – 8,99, 9 – 10). */
const GRADE_GRID = [1, 5, 7, 9, 10]
/** A grade is 1 + 9 × percent, so a grade line sits at this percent. */
const percentOfGrade = (grade: number) => ((grade - 1) / 9) * 100

/**
 * The chart's own width in CSS pixels, kept up to date: drawn at that width, 12 px text stays 12 px. A fixed
 * 640-unit drawing scaled into a 310 px phone card shrank every label to about 5 px.
 */
function useMeasuredWidth(): [(el: HTMLDivElement | null) => (() => void) | undefined, number] {
  const [width, setWidth] = useState(DEFAULT_WIDTH)
  const measure = useCallback((el: HTMLDivElement | null) => {
    if (!el || typeof ResizeObserver === 'undefined') return undefined
    const observer = new ResizeObserver(([entry]) => {
      const measured = Math.round(entry.contentRect.width)
      if (measured > 0) setWidth(Math.max(MIN_WIDTH, measured))
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [])
  return [measure, width]
}

/**
 * Grade-over-time line chart of graded attempts (oldest → newest, evenly spaced), on the 1–10 scale with the lines
 * of the grade bands. Plain SVG on theme colors, no chart library: one data series. Drawn at the width it gets, so
 * its text keeps its size on a phone. The data is repeated as a table below for screen readers and exact numbers -
 * unless `table` is false, on a page that lists the same tests with their grades already.
 */
export function ProgressChart({ points, table = true }: { points: ProgressPointDto[]; table?: boolean }) {
  const [measure, width] = useMeasuredWidth()
  if (points.length === 0) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="progress-empty">
        Nu există încă teste notate. Progresul apare după prima notă finală.
      </p>
    )
  }

  const innerW = width - PAD.left - PAD.right
  const innerH = HEIGHT - PAD.top - PAD.bottom
  const x = (i: number) => PAD.left + (points.length === 1 ? innerW / 2 : (i * innerW) / (points.length - 1))
  const y = (percent: number) => PAD.top + innerH - (percent / 100) * innerH
  const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${x(i)} ${y(p.percent)}`).join(' ')
  const grades = points.map((p) => romanianGrade(p.score, p.maxScore)).filter((g): g is number => g !== null)
  const average = grades.length > 0 ? formatGrade(grades.reduce((sum, g) => sum + g, 0) / grades.length) : '—'
  const summary = `Progres: ${roCount(points.length, 'test notat', 'teste notate')}, media notelor ${average}, ` +
    `ultima notă ${gradeOf(points[points.length - 1])}`

  return (
    <div ref={measure} className="space-y-3" data-testid="progress-chart">
      <svg viewBox={`0 0 ${width} ${HEIGHT}`} role="img" aria-label={summary} className="w-full">
        {GRADE_GRID.map((grade) => (
          <g key={grade}>
            <line x1={PAD.left} x2={width - PAD.right} y1={y(percentOfGrade(grade))} y2={y(percentOfGrade(grade))}
                  className="stroke-border" strokeWidth={1} />
            <text data-testid="axis-grade" x={PAD.left - 8} y={y(percentOfGrade(grade))} textAnchor="end"
                  dominantBaseline="middle" className="fill-muted-foreground text-xs">
              {grade}
            </text>
          </g>
        ))}
        <path d={path} fill="none" className="stroke-primary" strokeWidth={2.5} strokeLinejoin="round" />
        {points.map((p, i) => (
          <g key={p.attemptId}>
            <circle cx={x(i)} cy={y(p.percent)} r={5} className="fill-primary stroke-background" strokeWidth={2}>
              <title>{`${p.quizTitle} — nota ${gradeOf(p)}, ${p.score}/${p.maxScore} (${p.percent}%) · ${formatDate(p.submittedAt)}`}</title>
            </circle>
            {/* The grade over each point (under it near the top edge). */}
            <text data-testid="point-grade" x={x(i)} y={p.percent > 88 ? y(p.percent) + 20 : y(p.percent) - 10} textAnchor="middle"
                  className="fill-foreground text-xs font-medium">
              {gradeOf(p)}
            </text>
          </g>
        ))}
        {/* The day only: the hour is in the table below, and two full dates do not fit side by side on a phone. */}
        <text x={x(0)} y={HEIGHT - 8} textAnchor={points.length === 1 ? 'middle' : 'start'}
              className="fill-muted-foreground text-xs">
          {formatDay(points[0].submittedAt)}
        </text>
        {points.length > 1 && (
          <text x={x(points.length - 1)} y={HEIGHT - 8} textAnchor="end" className="fill-muted-foreground text-xs">
            {formatDay(points[points.length - 1].submittedAt)}
          </text>
        )}
      </svg>

      {table && (
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
                <span className="font-semibold tabular-nums">{gradeOf(p)}</span>{' '}
                <span className="text-muted-foreground">{p.score} / {p.maxScore} ({p.percent}%)</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      )}
    </div>
  )
}
