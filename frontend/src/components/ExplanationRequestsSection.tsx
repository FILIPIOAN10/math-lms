import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Card, CardContent } from '@/components/ui/card'
import { HomeSection } from '@/components/home/HomeBits'
import { listExplanationRequests, type ExplanationRequestsRow } from '@/lib/api'
import { roCount } from '@/lib/format'

/**
 * The teacher's tests where students asked „De ce am greșit?” and are waiting: each opens its Statistici, where the
 * options asked about say how many asked. Nothing at all when nobody is waiting or the list cannot be read.
 */
export function ExplanationRequestsSection() {
  const [rows, setRows] = useState<ExplanationRequestsRow[]>([])

  useEffect(() => {
    let cancelled = false
    listExplanationRequests()
      .then((list) => !cancelled && setRows(list))
      .catch(() => !cancelled && setRows([]))
    return () => {
      cancelled = true
    }
  }, [])

  if (rows.length === 0) return null
  return (
    <HomeSection title="Elevii întreabă „De ce am greșit?”">
      <Card>
        <CardContent className="p-0">
          <ul className="divide-y text-sm">
            {rows.map((row) => (
              <li key={row.quizId}>
                <Link to={`/admin/quizzes/${row.quizId}/stats`} className="flex flex-wrap items-baseline justify-between gap-x-4 px-3 py-2 hover:bg-muted">
                  <span className="font-medium">{row.quizTitle}</span>
                  <span className="text-xs text-muted-foreground">
                    {roCount(row.students, 'elev', 'elevi')}, {roCount(row.options, 'variantă', 'variante')}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
    </HomeSection>
  )
}
