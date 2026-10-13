import { Link } from 'react-router-dom'
import { Card, CardContent } from '@/components/ui/card'
import { MasteryLegend } from '@/components/MasteryLegend'
import { type EnClassMastery } from '@/lib/api'
import { EN_AREAS } from '@/lib/enChapters'
import { MASTERY_LABELS, MASTERY_TONES, masteryLevel } from '@/lib/mastery'

/** The class average of one EN chapter: the mean percent of the students who have data there. */
export function enAverage(grid: EnClassMastery, column: number): number | null {
  const known = grid.students.map((s) => s.cells[column]?.percent).filter((p): p is number => p !== null && p !== undefined)
  return known.length === 0 ? null : Math.round(known.reduce((a, b) => a + b, 0) / known.length)
}

/**
 * The teacher's grid of EN chapters for one class: students down, the EN chapters the class has met across (algebra,
 * then geometry; the code in the header, the title on hover), each cell coloured by level, the class average below.
 */
export function EnMasteryGrid({ grid }: { grid: EnClassMastery }) {
  if (grid.chapters.length === 0 || grid.students.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        Nimic încă: harta apare după ce elevii clasei dau un test EN și e corectat.
      </p>
    )
  }
  const areas = EN_AREAS.map((a) => ({ ...a, span: grid.chapters.filter((c) => c.area === a.value).length }))
    .filter((a) => a.span > 0)

  return (
    <div className="space-y-2">
      <Card>
        <CardContent className="overflow-x-auto p-0">
          <table aria-label="Nivel pe capitolele EN" className="w-full border-collapse text-sm">
            <thead>
              <tr>
                <th rowSpan={2} scope="col" className="sticky left-0 z-10 bg-card px-3 py-2 text-left font-medium">Elev</th>
                {areas.map((area) => (
                  <th key={area.value} colSpan={area.span} scope="colgroup"
                      className="border-l px-3 pt-2 text-center text-xs font-medium text-muted-foreground">
                    {area.label}
                  </th>
                ))}
              </tr>
              <tr>
                {grid.chapters.map((chapter) => (
                  <th key={chapter.id} scope="col" title={chapter.title} className="border-l px-3 pb-2 text-center font-mono font-medium">
                    {chapter.code}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {grid.students.map((student) => (
                <tr key={student.studentId} className="border-t">
                  <th scope="row" className="sticky left-0 z-10 bg-card px-3 py-2 text-left font-medium whitespace-nowrap">
                    <Link to={`/admin/students/${student.studentId}`} className="hover:underline">{student.fullName}</Link>
                  </th>
                  {student.cells.map((cell, i) => {
                    const level = masteryLevel(cell.percent)
                    const chapter = grid.chapters[i]
                    const label = cell.percent === null
                      ? `${student.fullName}, ${chapter.code} ${chapter.title}: ${MASTERY_LABELS.none}`
                      : `${student.fullName}, ${chapter.code} ${chapter.title}: ${cell.percent}%, ${MASTERY_LABELS[level]}`
                    return (
                      <td key={cell.chapterId} aria-label={label} title={label}
                          className={`border-l px-3 py-2 text-center font-medium tabular-nums ${MASTERY_TONES[level]}`}>
                        {cell.percent === null ? '—' : `${cell.percent}%`}
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="border-t-2">
                <th scope="row" className="sticky left-0 z-10 bg-card px-3 py-2 text-left font-medium">Media clasei</th>
                {grid.chapters.map((chapter, i) => {
                  const avg = enAverage(grid, i)
                  return (
                    <td key={chapter.id} data-testid={`en-average-${chapter.code}`}
                        className={`border-l px-3 py-2 text-center font-semibold tabular-nums ${MASTERY_TONES[masteryLevel(avg)]}`}>
                      {avg === null ? '—' : `${avg}%`}
                    </td>
                  )
                })}
              </tr>
            </tfoot>
          </table>
        </CardContent>
      </Card>
      <MasteryLegend />
    </div>
  )
}
