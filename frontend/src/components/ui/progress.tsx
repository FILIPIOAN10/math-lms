import { cn } from '@/lib/utils'

const FILL: Record<'primary' | 'success' | 'warning' | 'destructive', string> = {
  primary: 'bg-primary',
  success: 'bg-success',
  warning: 'bg-warning',
  destructive: 'bg-destructive',
}

/**
 * A thin bar for "how much of it is done" (7 din 12 predate, 3 / 8 întrebări). A progressbar to a screen reader, with
 * the words of `label` as its name and `valueText` read instead of a bare percentage.
 */
export function Progress({
  value,
  max,
  label,
  valueText,
  tone = 'primary',
  className,
  testId,
}: {
  value: number
  max: number
  label: string
  valueText?: string
  tone?: keyof typeof FILL
  className?: string
  testId?: string
}) {
  const share = max > 0 ? Math.min(1, Math.max(0, value / max)) : 0
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={max}
      aria-valuenow={value}
      aria-valuetext={valueText}
      data-testid={testId}
      className={cn('h-1.5 w-full overflow-hidden rounded-full bg-muted', className)}
    >
      <div
        className={cn('h-full rounded-full transition-[width] duration-300 motion-reduce:transition-none', FILL[tone])}
        style={{ width: `${share * 100}%` }}
      />
    </div>
  )
}
