/** „Ce notă iei la EN?": how the estimate is written and drawn. */

/** A grade the Romanian way: two decimals, comma ("7,40"). */
export function formatGrade(grade: number): string {
  return grade.toFixed(2).replace('.', ',')
}

/** The range as one phrase: "7,40 - 8,10", or a single grade when both ends meet (at 10). */
export function formatRange(low: number, high: number): string {
  return low === high ? formatGrade(low) : formatGrade(low) + ' - ' + formatGrade(high)
}

/** A gain in the estimate, with its sign: "+0,6". */
export function formatGain(gain: number): string {
  return '+' + gain.toFixed(1).replace('.', ',')
}

/** Where a grade sits on the 1 - 10 bar, in percent of its width (0 at 1, 100 at 10). */
export function barPercent(grade: number): number {
  return Math.min(100, Math.max(0, ((grade - 1) / 9) * 100))
}
