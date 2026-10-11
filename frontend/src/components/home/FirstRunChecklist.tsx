import { Link } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { HomeSection } from '@/components/home/HomeBits'
import { type FirstRunStep } from '@/lib/home'
import { cn } from '@/lib/utils'

/**
 * T7: the new teacher's first run — ① class → ② students → ③ test → ④ homework — on Acasă while any step is left.
 * Each step ticks itself off from the data; the next one to do carries the button.
 */
export function FirstRunChecklist({ steps }: { steps: FirstRunStep[] }) {
  const done = steps.filter((s) => s.done).length
  if (done === steps.length) return null
  const next = steps.find((s) => !s.done)

  return (
    <HomeSection title={`Primii pași (${done} din ${steps.length})`}>
      <Card data-testid="first-run">
        <CardContent className="py-2">
          <ol className="divide-y">
            {steps.map((step, i) => {
              const isNext = step === next
              return (
                <li
                  key={step.key}
                  data-testid={`first-run-${step.key}`}
                  data-done={step.done}
                  className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 py-3"
                >
                  <div className="flex min-w-0 items-start gap-3">
                    <span
                      aria-hidden
                      className={cn(
                        'mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full text-xs font-semibold',
                        step.done
                          ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300'
                          : isNext ? 'bg-foreground text-background' : 'bg-muted text-muted-foreground',
                      )}
                    >
                      {step.done ? '✓' : i + 1}
                    </span>
                    <div className="min-w-0">
                      <p className={cn('font-medium', step.done && 'text-muted-foreground line-through')}>
                        {step.label}
                        {step.done && <span className="sr-only"> (făcut)</span>}
                      </p>
                      {!step.done && <p className="text-sm text-muted-foreground">{step.hint}</p>}
                    </div>
                  </div>
                  {isNext && (
                    <Link to={step.to} className={buttonVariants({ size: 'sm' })}>
                      Începe
                    </Link>
                  )}
                </li>
              )
            })}
          </ol>
        </CardContent>
      </Card>
    </HomeSection>
  )
}
