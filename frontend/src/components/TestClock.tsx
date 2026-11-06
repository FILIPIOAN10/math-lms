import { clockText } from '@/lib/countdown'
import { cn } from '@/lib/utils'

/** In the last ten minutes the clock turns red: time to go back to the test. */
const URGENT_SECONDS = 10 * 60

/** The clock as the big card's pill: warm while there is time, red in the last ten minutes. */
export function ClockPill({ seconds }: { seconds: number }) {
  return (
    <span
      data-testid="clock-pill"
      className={cn(
        'rounded-full px-2 py-0.5 text-xs font-semibold',
        seconds < URGENT_SECONDS ? 'bg-red-600 text-white' : 'bg-warm text-neutral-900',
      )}
    >
      {seconds > 0 ? clockText(seconds) : '⏱ Timpul a expirat'}
    </span>
  )
}

/** The clock in a card's grey line, in place of the deadline: bold, red in the last ten minutes. */
export function ClockLine({ seconds, testId }: { seconds: number; testId?: string }) {
  return (
    <span
      data-testid={testId}
      className={cn('font-medium', seconds < URGENT_SECONDS ? 'text-destructive' : 'text-foreground')}
    >
      {clockText(seconds)}
    </span>
  )
}
