import { MathContent } from '@/components/MathContent'
import { cn } from '@/lib/utils'

export interface PartLike {
  label: string
  statement: string
  points: number
  /** The barem, where the reader may see it (teacher, or the student after the result). */
  barem?: string | null
  /** Points given for this requirement; null = not graded yet; absent = not shown. */
  awardedPoints?: number | null
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
          <div className="flex gap-2 text-sm">
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
