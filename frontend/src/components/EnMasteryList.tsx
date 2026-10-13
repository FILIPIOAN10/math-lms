import { Link } from 'react-router-dom'
import { MasteryLegend } from '@/components/MasteryLegend'
import { type EnChapterMastery } from '@/lib/api'
import { EN_AREAS } from '@/lib/enChapters'
import { roCount } from '@/lib/format'
import { MASTERY_BARS, MASTERY_LABELS, MASTERY_TONES, masteryLevel } from '@/lib/mastery'

/** How many weak chapters the "start here" line names. */
const WEAKEST = 3

/** A chapter's name, linked to its page when the page has something on it. */
function ChapterName({ row }: { row: EnChapterMastery }) {
  const name = (
    <>
      <span className="mr-1 font-mono text-xs text-muted-foreground">{row.code}</span> {row.title}
    </>
  )
  return row.ready
    ? <Link to={`/capitole/${row.chapterId}`} className="min-w-0 truncate font-medium hover:underline">{name}</Link>
    : <span className="min-w-0 truncate font-medium">{name}</span>
}

/**
 * One student's level in each EN chapter they have met (their own page, their parent's, the teacher's view of them):
 * first the weakest chapters to revise, then algebra and geometry with a bar each. Chapters not met yet are left out.
 */
export function EnMasteryList({ rows }: { rows: EnChapterMastery[] }) {
  const met = rows.filter((r) => r.questions > 0 && r.percent !== null)
  if (met.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        Încă nicio întrebare de EN notată. Harta apare după primul test EN corectat.
      </p>
    )
  }
  const weakest = [...met].filter((r) => masteryLevel(r.percent) === 'low')
    .sort((a, b) => (a.percent ?? 0) - (b.percent ?? 0)).slice(0, WEAKEST)

  return (
    <div className="space-y-4">
      {weakest.length > 0 && (
        <div className="rounded-lg border border-red-500/30 bg-red-500/5 p-3 text-sm" data-testid="en-weakest">
          <p className="font-medium">De recapitulat întâi:</p>
          <ul className="mt-1 space-y-1">
            {weakest.map((row) => (
              <li key={row.chapterId} className="flex flex-wrap items-baseline gap-x-2">
                <ChapterName row={row} />
                <span className="text-xs text-muted-foreground">{row.percent}%</span>
              </li>
            ))}
          </ul>
        </div>
      )}
      {EN_AREAS.map((area) => {
        const list = met.filter((r) => r.area === area.value)
        if (list.length === 0) return null
        return (
          <div key={area.value} role="group" aria-label={area.label} className="space-y-2">
            <h3 className="text-sm font-medium text-muted-foreground">{area.label}</h3>
            <ul className="space-y-2">
              {list.map((row) => {
                const level = masteryLevel(row.percent)
                return (
                  <li key={row.chapterId} className="grid grid-cols-[1fr_auto] items-center gap-x-3 gap-y-1 text-sm" data-testid="en-mastery-row">
                    <ChapterName row={row} />
                    <span className={`rounded-md px-2 py-0.5 text-xs font-medium ${MASTERY_TONES[level]}`}>
                      {`${row.percent}% · ${MASTERY_LABELS[level]}`}
                    </span>
                    <span className="col-span-2 flex items-center gap-2">
                      <span className="h-2 flex-1 overflow-hidden rounded-full bg-muted" aria-hidden="true">
                        <span className={`block h-full ${MASTERY_BARS[level]}`} style={{ width: `${row.percent ?? 0}%` }} />
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {row.earned} / {row.possible} p · {roCount(row.questions, 'întrebare', 'întrebări')}
                      </span>
                    </span>
                  </li>
                )
              })}
            </ul>
          </div>
        )
      })}
      <MasteryLegend />
    </div>
  )
}
