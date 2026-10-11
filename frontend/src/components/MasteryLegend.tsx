import { MASTERY_LABELS, MASTERY_TONES, type MasteryLevel } from '@/lib/mastery'

const LEVELS: { level: MasteryLevel; range: string }[] = [
  { level: 'high', range: '80–100%' },
  { level: 'mid', range: '50–79%' },
  { level: 'low', range: 'sub 50%' },
  { level: 'none', range: 'nimic încă' },
]

/** What the colours mean. */
export function MasteryLegend() {
  return (
    <p className="flex flex-wrap gap-2 text-xs" aria-label="Legendă">
      {LEVELS.map(({ level, range }) => (
        <span key={level} className={`rounded-md px-2 py-0.5 ${MASTERY_TONES[level]}`}>
          {MASTERY_LABELS[level]} · {range}
        </span>
      ))}
    </p>
  )
}
