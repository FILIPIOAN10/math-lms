import { MathContent } from '@/components/MathContent'
import { ResultCheckLine } from '@/components/ResultCheckLine'
import type { ResultCheck } from '@/lib/api'
import { cn } from '@/lib/utils'

export interface PartLike {
  label: string
  statement: string
  points: number
  /** The barem, where the reader may see it (teacher, or the student after the result). */
  barem?: string | null
  /** Points given for this requirement; null = not graded yet; absent = not shown. */
  awardedPoints?: number | null
  /** The final result the student typed for it and what it says against the barem's (the result page only). */
  typedResult?: string | null
  expectedResult?: string | null
  resultCheck?: ResultCheck | null
}

/**
 * An open problem's requirements as on the EN paper: "(2p) a) Arată că x = 25.", one per line, each with its barem and
 * the points given when the reader may see them.
 */
export function PartsView({ parts, className }: { parts: PartLike[]; className?: string }) {
  if (parts.length === 0) return null
  return (
    <ol className={cn('space-y-2', className)} data-testid="item-parts">
      {parts.map((part) => (
        <li key={part.label} className="space-y-1">
          <div className="flex gap-2 text-sm max-sm:text-base">
            <span className="shrink-0 font-medium text-muted-foreground tabular-nums">({part.points}p)</span>
            <span className="shrink-0 font-semibold">{part.label})</span>
            <MathContent className="min-w-0 flex-1">{part.statement}</MathContent>
            {part.awardedPoints !== undefined && (
              <span
                className={cn(
                  'shrink-0 text-sm font-medium tabular-nums',
                  part.awardedPoints === null ? 'text-muted-foreground' : 'text-emerald-700 dark:text-emerald-300',
                )}
              >
                {part.awardedPoints === null ? '—' : part.awardedPoints} / {part.points} p
              </span>
            )}
          </div>
          <ResultCheckLine
            audience="student"
            check={part.resultCheck}
            typed={part.typedResult}
            expected={part.expectedResult}
            className="ml-14 text-xs"
          />
          {part.barem && (
            <div className="ml-14 rounded-md bg-muted p-2 text-xs">
              <span className="font-medium">Barem {part.label}): </span>
              <MathContent inline>{part.barem}</MathContent>
            </div>
          )}
        </li>
      ))}
    </ol>
  )
}
