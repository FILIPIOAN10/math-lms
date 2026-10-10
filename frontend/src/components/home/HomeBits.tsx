import { useId, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

/** A titled block of the home page, announced as a region under its heading. */
export function HomeSection({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  const headingId = useId()
  return (
    <section aria-labelledby={headingId} className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <h2 id={headingId} className="font-medium">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  )
}

/**
 * One number with what it counts, the whole tile a link to where it is dealt with. A non-zero count the user has to
 * act on is shown in the accent colour; zero stays quiet.
 */
export function StatTile({
  to,
  value,
  label,
  urgent = false,
  testId,
}: {
  to: string
  value: number
  label: string
  urgent?: boolean
  testId?: string
}) {
  return (
    <Link
      to={to}
      data-testid={testId}
      className="block rounded-xl outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
    >
      <Card className="h-full transition-colors hover:bg-background/60">
        <CardContent className="py-4">
          <p className={cn('text-3xl font-semibold', urgent && value > 0 ? 'text-destructive' : 'text-foreground')}>
            {value}
          </p>
          <p className="text-sm text-muted-foreground">{label}</p>
        </CardContent>
      </Card>
    </Link>
  )
}

/** The calm "nothing here" line inside a card, with an optional way forward. */
export function HomeEmpty({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <Card>
      <CardContent className="flex flex-wrap items-center justify-between gap-3 py-4 text-sm text-muted-foreground">
        <p>{children}</p>
        {action}
      </CardContent>
    </Card>
  )
}
