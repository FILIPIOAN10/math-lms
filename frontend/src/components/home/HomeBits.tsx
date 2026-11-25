import { useId, type ReactNode } from 'react'
import { type LucideIcon } from 'lucide-react'
import { Link } from 'react-router-dom'
import { EmptyArt } from '@/components/EmptyArt'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

/** A titled block of the home page, announced as a region under its heading. */
export function HomeSection({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  const headingId = useId()
  return (
    <section aria-labelledby={headingId} className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        {/* A small caps label: the cards under it carry the weight, the label only says what the group is. */}
        <h2 id={headingId} className="text-xs font-semibold tracking-wider text-muted-foreground uppercase">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  )
}

/**
 * One number with what it counts, the whole tile a link to where it is dealt with. A non-zero count the user has to
 * act on is shown in the accent colour; with `doneLabel`, zero is good news: the tile keeps its number and label (so
 * every tile has the same shape) and adds a green "Totul la zi" chip, with `doneLabel` said to a screen reader.
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
      data-done={done || undefined}
      className="block rounded-xl outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
    >
      <Card className="hover-lift h-full">
        <CardContent className="flex flex-col gap-3 py-4">
          {/* Wraps on a phone, where three tiles share the row: the chip drops under the icon instead of being cut. */}
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span
              className={cn(
                'flex size-9 shrink-0 items-center justify-center rounded-lg',
                done ? 'bg-success/15 text-success-foreground' : pressing ? 'bg-destructive/10 text-destructive' : 'bg-primary/10 text-primary',
              )}
            >
              <Icon aria-hidden="true" className="size-5" />
            </span>
            {done && <Badge variant="success" aria-hidden="true">Totul la zi</Badge>}
          </div>
          <div>
            <p className={cn('text-3xl font-semibold tabular-nums', pressing ? 'text-destructive' : 'text-foreground')}>{value}</p>
            <p className="text-sm text-muted-foreground">{label}</p>
            {done && <span className="sr-only">{doneLabel}</span>}
          </div>
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

/**
 * The home page of a role on a wide screen: what to act on in the wide column, the rest in a fixed-width column beside
 * it, so the side never stretches. `stats` (the counts) go on top of the side column on a computer, and first of all
 * on a phone. Under `lg`, and with neither `side` nor `stats`, everything is one column, in the order stats, main, side.
 */
export function HomeColumns({ main, side, stats }: { main: ReactNode; side?: ReactNode; stats?: ReactNode }) {
  const twoColumns = Boolean(side || stats)
  return (
    <div
      data-testid="home-columns"
      // Row 1 is as tall as the counts; the main column spans both rows, so the side's sections follow the counts
      // directly instead of waiting for the end of the first main section.
      className={cn('grid gap-6', twoColumns && 'lg:grid-cols-[minmax(0,1fr)_340px] lg:grid-rows-[auto_1fr] lg:items-start')}
    >
      {stats && <div data-testid="home-stats" className="min-w-0 lg:col-start-2 lg:row-start-1">{stats}</div>}
      <div
        data-testid="home-main"
        className={cn('min-w-0 space-y-6', twoColumns && 'lg:col-start-1 lg:row-span-2 lg:row-start-1')}
      >
        {main}
      </div>
      {side && (
        <div data-testid="home-side" className={cn('min-w-0 space-y-6 lg:col-start-2', stats ? 'lg:row-start-2' : 'lg:row-start-1')}>
          {side}
        </div>
      )}
    </div>
  )
}

/** The calm "nothing here" card: a picture, the sentence and the way forward, centred. */
export function HomeEmpty({ children, action, symbol }: { children: ReactNode; action?: ReactNode; symbol?: string }) {
  return (
    <Card>
      <CardContent className="flex flex-col items-center gap-3 py-8 text-center text-sm text-muted-foreground">
        {symbol && <EmptyArt symbol={symbol} />}
        <p className="max-w-xs">{children}</p>
        {action}
      </CardContent>
    </Card>
  )
}
