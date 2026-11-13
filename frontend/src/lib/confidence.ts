import type { Confidence } from '@/lib/api'

/** The three taps, in the order shown (worded so they fit a girl and a boy alike). */
export const CONFIDENCE_CHOICES: { value: Confidence; label: string }[] = [
  { value: 'GUESS', label: 'Am ghicit' },
  { value: 'THINK', label: 'Cred că da' },
  { value: 'SURE', label: 'Știu sigur' },
]

/** Fewer "Știu sigur" answers than this say nothing yet: 1 right out of 1 is not "always right". */
export const CALIBRATION_MIN = 5

/** How many of the answers tapped "Știu sigur" were right, as the server counts them. */
export interface Calibration {
  sure: number
  sureCorrect: number
}

/** "Când ai zis „Știu sigur”, ai avut dreptate la 8 din 10." - or null while there are too few to say anything. */
export function calibrationLine(calibration: Calibration | null | undefined): string | null {
  if (!calibration || calibration.sure < CALIBRATION_MIN) return null
  return `Când ai zis „Știu sigur”, ai avut dreptate la ${calibration.sureCorrect} din ${calibration.sure}.`
}
