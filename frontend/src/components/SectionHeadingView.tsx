import { type SectionHeading } from '@/lib/sections'
import { cn } from '@/lib/utils'

/** The heading above the first question of a section ("SUBIECTUL I" + its instruction), as on the EN paper. */
export function SectionHeadingView({ heading, className }: { heading: SectionHeading; className?: string }) {
  return (
    <div className={cn('pt-2', className)} data-testid="section-heading">
      <h3 className="text-sm font-semibold tracking-wide uppercase">{heading.title}</h3>
      {heading.note && <p className="text-sm whitespace-pre-line text-muted-foreground">{heading.note}</p>}
    </div>
  )
}
