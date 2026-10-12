import { roCount } from '@/lib/format'
import { type SectionHeading } from '@/lib/sections'
import { cn } from '@/lib/utils'

/** The heading above the first question of a section ("SUBIECTUL I" + its instruction), as on the EN paper. */
export function SectionHeadingView({ heading, className }: { heading: SectionHeading; className?: string }) {
  return (
    <div className={cn('pt-2', className)} data-testid="section-heading">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3">
        <h3 className="text-sm font-semibold tracking-wide uppercase">{heading.title}</h3>
        {heading.total !== undefined && heading.total > 0 && (
          <span className="text-sm font-medium text-muted-foreground">({roCount(heading.total, 'punct', 'puncte')})</span>
        )}
      </div>
      {heading.note && <p className="text-sm whitespace-pre-line text-muted-foreground">{heading.note}</p>}
    </div>
  )
}
