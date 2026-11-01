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

/**
 * The chapters to go back to first, weakest first, by name: a parent reads this, so no codes like "A3". A chapter of the
 * class and an EN one can have the same name: it counts once.
 */
export function chaptersToRevise(
  chapters: { chapterTitle: string; percent: number | null }[],
  enChapters: { title: string; percent: number | null }[],
  limit = 3,
): string[] {
  const weak = [
    ...chapters.map((c) => ({ title: c.chapterTitle, percent: c.percent })),
    ...enChapters.map((c) => ({ title: c.title, percent: c.percent })),
  ].filter((c) => masteryLevel(c.percent) === 'low').sort((a, b) => (a.percent ?? 0) - (b.percent ?? 0))
  return [...new Set(weak.map((c) => c.title))].slice(0, limit)
}

/** Below this many students with data, a "class average" is really one or two children: shown, but not coloured. */
export const MIN_STUDENTS_FOR_AVERAGE = 3

/** The mean of the percents that exist (rounded), and how many students it is from; null when nobody has data. */
export function classAverage(percents: (number | null | undefined)[]): { percent: number; students: number } | null {
  const known = percents.filter((p): p is number => p !== null && p !== undefined)
  if (known.length === 0) return null
  return { percent: Math.round(known.reduce((a, b) => a + b, 0) / known.length), students: known.length }
}
