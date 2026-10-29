/**
 * The 1–10 grade ("nota") from points, Romanian exam style with one point "din oficiu": 1 + 9 × the share of points
 * earned, to two decimals. 0% is a 1, half is a 5.50, everything is a 10. A quiz worth no points has no grade.
 */
export function romanianGrade(score: number, maxScore: number): number | null {
  if (maxScore <= 0) return null
  return Math.round((1 + (9 * score) / maxScore) * 100) / 100
}

/** Where a grade falls: under 5 is failed, 5 up to 8 passed, 8 and above good. */
export type GradeBand = 'low' | 'mid' | 'high'

export function gradeBand(grade: number): GradeBand {
  return grade < 5 ? 'low' : grade < 8 ? 'mid' : 'high'
}

/**
 * The grade's colour, wherever a grade is a number on screen. The -700 shades keep 4.5:1 on white for small text;
 * in the dark theme the -400 shades do the same on the dark cards. The colour is a hint, never the only sign: the
 * number is always there.
 */
export const GRADE_TEXT: Record<GradeBand, string> = {
  low: 'text-red-700 dark:text-red-400',
  mid: 'text-amber-700 dark:text-amber-400',
  high: 'text-emerald-700 dark:text-emerald-400',
}

/** The same bands for a filled shape (a bar), where 3:1 is enough. */
export const GRADE_FILL: Record<GradeBand, string> = {
  low: 'bg-red-500',
  mid: 'bg-amber-500',
  high: 'bg-emerald-500',
}

/** ...and for the ring around a grade. */
export const GRADE_STROKE: Record<GradeBand, string> = {
  low: 'stroke-red-500',
  mid: 'stroke-amber-500',
  high: 'stroke-emerald-500',
}

/** "10" for a whole grade, otherwise two decimals with a Romanian comma: "5,50", "7,55". */
export function formatGrade(grade: number): string {
  if (Number.isInteger(grade)) return String(grade)
  return grade.toLocaleString('ro-RO', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** One line for a score wherever it is listed: "8 / 11 p · nota 7,55" (no grade for a quiz worth nothing). */
export function scoreLine(score: number, maxScore: number): string {
  const grade = romanianGrade(score, maxScore)
  return grade === null ? `${score} / ${maxScore} p` : `${score} / ${maxScore} p · nota ${formatGrade(grade)}`
}
