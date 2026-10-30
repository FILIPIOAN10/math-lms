import { useId, type ReactNode } from 'react'
import { type LucideIcon } from 'lucide-react'
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
 * act on is shown in the accent colour; with `doneLabel`, zero is good news and says so in green instead of a bare 0.
 */
export function StatTile({
  to,
  value,
  label,
  icon: Icon,
  urgent = false,
  doneLabel,
  testId,
}: {
  to: string
  value: number
  label: string
  icon: LucideIcon
  urgent?: boolean
  doneLabel?: string
  testId?: string
}) {
  const done = doneLabel !== undefined && value === 0
  const pressing = urgent && value > 0
  return (
    <Link
      to={to}
      data-testid={testId}
      className="block rounded-xl outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
    >
      <Card className="hover-lift h-full">
        <CardContent className="flex items-start justify-between gap-2 py-4">
          {done ? (
            <p className="text-lg font-semibold text-emerald-700 dark:text-emerald-300">{doneLabel} ✓</p>
          ) : (
            <div>
              <p className={cn('text-3xl font-semibold', pressing ? 'text-destructive' : 'text-foreground')}>{value}</p>
              <p className="text-sm text-muted-foreground">{label}</p>
            </div>
          )}
          <Icon
            aria-hidden="true"
            className={cn('size-5 shrink-0', done ? 'text-emerald-600 dark:text-emerald-400' : pressing ? 'text-destructive' : 'text-primary')}
          />
        </CardContent>
      </Card>
    </Link>
  )
}

/** A page worth a click from Acasă: its name and what it is for. */
export function HomeShortcut({ to, icon: Icon, title, text }: { to: string; icon: LucideIcon; title: string; text: string }) {
  return (
    <Link to={to} className="block rounded-xl outline-none focus-visible:ring-3 focus-visible:ring-ring/50">
      <Card className="hover-lift h-full">
        <CardContent className="flex items-center gap-3 py-3">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
            <Icon aria-hidden="true" className="size-5" />
          </span>
          <span className="min-w-0">
            <span className="block font-medium">{title}</span>
            <span className="block text-sm text-muted-foreground">{text}</span>
          </span>
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
