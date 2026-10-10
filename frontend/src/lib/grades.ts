/**
 * The 1–10 grade ("nota") from points, Romanian exam style with one point "din oficiu": 1 + 9 × the share of points
 * earned, to two decimals. 0% is a 1, half is a 5.50, everything is a 10. A quiz worth no points has no grade.
 */
export function romanianGrade(score: number, maxScore: number): number | null {
  if (maxScore <= 0) return null
  return Math.round((1 + (9 * score) / maxScore) * 100) / 100
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
