import { useEffect, useState } from 'react'
import { Card, CardContent } from '@/components/ui/card'
import { getAiUsage as getAiUsageRequest, type AiUsageRow } from '@/lib/api'

const FEATURE_LABELS: Record<string, string> = {
  GRADING: 'Propuneri de notă',
  BAREM: 'Barem din poze',
  IMPORT: 'Import din poze',
  CHAPTERS: 'Sugestii de capitole',
  MISTAKES: 'Explicații de greșeli',
}

const number = new Intl.NumberFormat('ro-RO')

/**
 * This month's AI bill for the admin, one line per feature and model: calls and tokens (output includes the thinking,
 * the expensive part). Every billed call counts, also the ones whose answer was then refused. Nothing shows while the
 * month has no calls, and a failed load hides the card: the journal below works without it.
 */
export function AiUsageCard({ getAiUsage = getAiUsageRequest }: { getAiUsage?: typeof getAiUsageRequest }) {
  const [rows, setRows] = useState<AiUsageRow[] | null>(null)

  useEffect(() => {
    let cancelled = false
    getAiUsage()
      .then((usage) => !cancelled && setRows(usage.rows))
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [getAiUsage])

  if (!rows || rows.length === 0) return null
  return (
    <Card>
      <CardContent className="space-y-2 py-4">
        <h2 className="text-sm font-medium">Consum AI luna aceasta</h2>
        <div className="overflow-x-auto">
          <table aria-label="Consum AI luna aceasta" className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-muted-foreground">
                <th scope="col" className="py-1 pr-3 font-medium">Ce</th>
                <th scope="col" className="py-1 pr-3 font-medium">Model</th>
                <th scope="col" className="py-1 pr-3 text-right font-medium">Apeluri</th>
                <th scope="col" className="py-1 pr-3 text-right font-medium">Tokeni intrare</th>
                <th scope="col" className="py-1 text-right font-medium">Tokeni ieșire</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={`${r.feature}-${r.model}`} className="border-t">
                  <th scope="row" className="py-1 pr-3 text-left font-normal">{FEATURE_LABELS[r.feature] ?? r.feature}</th>
                  <td className="py-1 pr-3 text-muted-foreground">{r.model}</td>
                  <td className="py-1 pr-3 text-right tabular-nums">{number.format(r.calls)}</td>
                  <td className="py-1 pr-3 text-right tabular-nums">{number.format(r.inputTokens)}</td>
                  <td className="py-1 text-right tabular-nums">{number.format(r.outputTokens)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="text-xs text-muted-foreground">
          Fiecare apel plătit, și cele al căror răspuns a fost respins. Tokenii de ieșire includ „gândirea” modelului și
          costă de câteva ori mai mult decât cei de intrare.
        </p>
      </CardContent>
    </Card>
  )
}
