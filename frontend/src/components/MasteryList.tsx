import { MasteryLegend } from '@/components/MasteryLegend'
import { type ChapterMastery } from '@/lib/api'
import { roCount } from '@/lib/format'
import { MASTERY_BARS, MASTERY_LABELS, MASTERY_TONES, masteryLevel } from '@/lib/mastery'

/**
 * One student's level per chapter (their own page, or their parent's), grouped by book: a bar, the percent and the
 * level in words. Built from the latest answer to each question tagged with the chapter - tests and practice alike.
 */
export function MasteryList({ rows }: { rows: ChapterMastery[] }) {
  if (rows.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        Niciun capitol încă. Harta apare când profesorul leagă testele de capitolele clasei.
      </p>
    )
  }
  const severalClasses = new Set(rows.map((r) => r.schoolClassName)).size > 1
  const groups: { label: string; rows: ChapterMastery[] }[] = []
  for (const row of rows) {
    const label = severalClasses ? `${row.bookTitle} (${row.schoolClassName})` : row.bookTitle
    const group = groups.find((g) => g.label === label)
    if (group) group.rows.push(row)
    else groups.push({ label, rows: [row] })
  }

  return (
    <div className="space-y-4">
      {groups.map((group) => (
        <div key={group.label} role="group" aria-label={group.label} className="space-y-2">
          <h3 className="text-sm font-medium text-muted-foreground">{group.label}</h3>
          <ul className="space-y-2">
            {group.rows.map((row) => {
              const level = masteryLevel(row.percent)
              return (
                <li key={row.chapterId} className="grid grid-cols-[1fr_auto] items-center gap-x-3 gap-y-1 text-sm">
                  <span className="min-w-0 truncate font-medium">{row.chapterTitle}</span>
                  <span className={`rounded-md px-2 py-0.5 text-xs font-medium ${MASTERY_TONES[level]}`}>
                    {row.percent === null ? MASTERY_LABELS.none : `${row.percent}% · ${MASTERY_LABELS[level]}`}
                  </span>
                  <span className="col-span-2 flex items-center gap-2">
                    <span className="h-2 flex-1 overflow-hidden rounded-full bg-muted" aria-hidden="true">
                      <span className={`block h-full ${MASTERY_BARS[level]}`} style={{ width: `${row.percent ?? 0}%` }} />
                    </span>
                    {row.questions > 0 && (
                      <span className="text-xs text-muted-foreground">{roCount(row.questions, 'întrebare', 'întrebări')}</span>
                    )}
                  </span>
                </li>
              )
            })}
          </ul>
        </div>
      ))}
      <MasteryLegend />
    </div>
  )
}
