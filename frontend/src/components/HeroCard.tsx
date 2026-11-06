import type { ReactNode } from 'react'
import { Card, CardContent } from '@/components/ui/card'

/**
 * The student page's one big card: what to do next - the soonest homework, or a timed test the student left while its
 * clock runs. `testId` sits on the content, where the lists' plain cards keep theirs.
 */
export function HeroCard({ kicker, title, meta, note, action, testId }: {
  kicker: string
  title: string
  meta: ReactNode
  note?: string
  action?: ReactNode
  testId: string
}) {
  return (
    <Card className="hero-surface relative bg-primary text-white ring-0">
      {/* A big pale π behind the text: a maths textbook's margin, not an illustration to be read. */}
      <span aria-hidden="true" className="pointer-events-none absolute -right-3 -bottom-8 text-9xl leading-none font-bold text-white/10 select-none">
        π
      </span>
      <CardContent data-testid={testId} className="relative flex flex-wrap items-center justify-between gap-4 py-3">
        <div className="min-w-0 flex-1 basis-56 space-y-2">
          <p className="text-xs font-medium tracking-wide text-white/80 uppercase">{kicker}</p>
          <p className="text-xl leading-snug font-semibold break-words">{title}</p>
          <p className="flex flex-wrap items-center gap-2 text-sm text-white/90">{meta}</p>
          {note && <p className="text-sm text-white/90">{note}</p>}
        </div>
        {action}
      </CardContent>
    </Card>
  )
}
