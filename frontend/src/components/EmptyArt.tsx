import { Flame } from 'lucide-react'
import { cn } from '@/lib/utils'

/**
 * The picture of an empty state: a maths symbol on a soft indigo tile with two dots, instead of a bare grey sentence.
 * Decorative - the sentence beside it says everything. Never a digit: next to a grade it would read as one.
 */
export function EmptyArt({ symbol = 'x²', className }: { symbol?: string; className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'relative inline-flex size-14 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-xl font-semibold text-primary',
        className,
      )}
    >
      {symbol}
      <span className="absolute -top-1 -right-1 size-3 rounded-full bg-warm" />
      <span className="absolute -bottom-1 -left-1 size-2 rounded-full bg-primary/40" />
    </span>
  )
}

/** The streak as a warm pill with a flame ("seria 3 zile"), instead of an emoji tacked on the end of a line. */
export function StreakPill({ children }: { children: React.ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-warm-soft px-2 py-0.5 text-xs font-semibold text-warm-foreground">
      <Flame aria-hidden="true" className="size-3.5 text-warm" fill="currentColor" />
      {children}
    </span>
  )
}
