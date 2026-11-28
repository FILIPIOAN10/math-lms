import { Link } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { type ClassGoalRow } from '@/lib/api'
import { formatGrade, formatRange } from '@/lib/enPrediction'

/**
 * „Nota estimată la EN și ținta" for a class: each student's estimate, the target they picked and, in red, „în urmă"
 * when they are more than 0,3 under the straight line to it. A name opens the student's page, where their plan is.
 */
export function ClassGoalsTable({ rows }: { rows: ClassGoalRow[] }) {
  return (
    <Card>
      <CardContent className="overflow-x-auto p-0">
        <table aria-label="Nota estimată la EN și ținta" className="w-full border-collapse text-sm">
          <thead>
            <tr className="text-left text-muted-foreground">
              <th scope="col" className="px-3 py-2 font-medium">Elev</th>
              <th scope="col" className="px-3 py-2 font-medium">Prognoză</th>
              <th scope="col" className="px-3 py-2 font-medium">Țintă</th>
              <th scope="col" className="px-3 py-2 font-medium"><span className="sr-only">Ritm</span></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.studentId} className="border-t">
                <th scope="row" className="px-3 py-2 text-left font-medium whitespace-nowrap">
                  <Link to={`/admin/students/${r.studentId}`} className="hover:underline">{r.fullName}</Link>
                </th>
                <td className="px-3 py-2 tabular-nums"
                    title={r.low !== null && r.high !== null ? formatRange(r.low, r.high) : undefined}>
                  {r.grade === null ? <span className="text-muted-foreground">prea puține răspunsuri</span> : '~' + formatGrade(r.grade)}
                </td>
                <td className="px-3 py-2 tabular-nums">
                  {r.target === null ? <span className="text-muted-foreground">fără țintă</span> : formatGrade(r.target)}
                </td>
                <td className="px-3 py-2">
                  {r.behind && (
                    <Badge variant="destructive"
                           title={r.expectedToday === null ? undefined : `Azi ar trebui să fie pe la ~${formatGrade(r.expectedToday)}`}>
                      în urmă
                    </Badge>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </CardContent>
    </Card>
  )
}
