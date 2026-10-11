export type MasteryLevel = 'high' | 'mid' | 'low' | 'none'

/** Mastered from 80%, in progress from 50%, to review below that; no scored answer = no level. */
export function masteryLevel(percent: number | null): MasteryLevel {
  if (percent === null) return 'none'
  if (percent >= 80) return 'high'
  if (percent >= 50) return 'mid'
  return 'low'
}

/** A word for every level, shown next to the colour - the colour is never the only signal. */
export const MASTERY_LABELS: Record<MasteryLevel, string> = {
  high: 'Stăpânit',
  mid: 'În lucru',
  low: 'De recapitulat',
  none: 'Fără date',
}

/** Cell / chip background + text, tuned for both themes. */
export const MASTERY_TONES: Record<MasteryLevel, string> = {
  high: 'bg-emerald-500/20 text-emerald-900 dark:text-emerald-100',
  mid: 'bg-amber-400/25 text-amber-900 dark:text-amber-100',
  low: 'bg-red-500/20 text-red-900 dark:text-red-100',
  none: 'bg-muted text-muted-foreground',
}

/** The filled part of a progress bar. */
export const MASTERY_BARS: Record<MasteryLevel, string> = {
  high: 'bg-emerald-500',
  mid: 'bg-amber-400',
  low: 'bg-red-500',
  none: 'bg-transparent',
}
