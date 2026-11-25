import { useEffect, useState } from 'react'
import { Check } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Confetti } from '@/components/Confetti'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { HomeSection } from '@/components/home/HomeBits'
import { type FirstRunStep } from '@/lib/home'
import { cn } from '@/lib/utils'

/** The teacher pressed "Ascunde": the checklist stays away on this browser. */
export const FIRST_RUN_HIDDEN = 'firstRunHidden'
/** The checklist was shown with a step left: only then is finishing it worth a "Gata!". */
const FIRST_RUN_SEEN = 'firstRunSeen'
/** The "Gata!" card was shown once already. */
const FIRST_RUN_CELEBRATED = 'firstRunCelebrated'

function stored(key: string): boolean {
  try {
    return localStorage.getItem(key) === '1'
  } catch {
    return false
  }
}

function store(key: string) {
  try {
    localStorage.setItem(key, '1')
  } catch {
    // Private mode without storage: the checklist simply comes back next time.
  }
}

/**
 * T7: the new teacher's first run — ① class → ② students → ③ test → ④ homework — on Acasă while any step is left.
 * Each step ticks itself off from the data; the steps done fold into one green line, the next one carries the button.
 * "Ascunde" puts it away on this browser. Finishing the last step, after having seen the list, earns a one-time "Gata!".
 */
export function FirstRunChecklist({ steps }: { steps: FirstRunStep[] }) {
  const [hidden, setHidden] = useState(() => stored(FIRST_RUN_HIDDEN))
  const done = steps.filter((s) => s.done).length
  const allDone = done === steps.length
  // Decided once, on the first render: re-renders must not flip the card on or off.
  const [celebrate] = useState(() => allDone && stored(FIRST_RUN_SEEN) && !stored(FIRST_RUN_CELEBRATED))

  useEffect(() => {
    if (allDone && celebrate) store(FIRST_RUN_CELEBRATED)
    else if (!allDone && !hidden) store(FIRST_RUN_SEEN)
  }, [allDone, celebrate, hidden])

  if (allDone) {
    if (!celebrate) return null
    return (
      <Card data-testid="first-run-done" className="border-success/40 bg-success/10">
        <Confetti />
        <CardContent className="flex items-center gap-3 py-4">
          <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-full bg-success/20 text-success-foreground">
            <Check className="size-5" />
          </span>
          <div>
            <p className="font-semibold">Gata! Totul e pregătit 🎉</p>
            <p className="text-sm text-muted-foreground">Clasa, elevii, testul și tema sunt la locul lor.</p>
          </div>
        </CardContent>
      </Card>
    )
  }
  if (hidden) return null

  const doneSteps = steps.filter((s) => s.done)
  const todo = steps.filter((s) => !s.done)
  const next = todo[0]

  return (
    <HomeSection
      title="Primii pași"
      action={
        <Button
          variant="ghost"
          size="sm"
          onClick={() => {
            store(FIRST_RUN_HIDDEN)
            setHidden(true)
          }}
        >
          Ascunde
        </Button>
      }
    >
      <Card data-testid="first-run">
        <CardContent className="space-y-2 py-3">
          <Progress value={done} max={steps.length} label="Primii pași" valueText={`${done} din ${steps.length}`} />
          <p className="text-xs text-muted-foreground">{done} din {steps.length} făcuți</p>
          {doneSteps.length > 0 && (
            <p data-testid="first-run-done-steps" className="flex items-center gap-2 py-1 text-sm text-success-foreground">
              <Check aria-hidden="true" className="size-4 shrink-0" />
              <span>
                <span className="sr-only">Făcut: </span>
                {doneSteps.map((s) => s.label).join(' · ')}
              </span>
            </p>
          )}
          <ol className="divide-y">
            {todo.map((step) => {
              const isNext = step === next
              return (
                <li
                  key={step.key}
                  data-testid={`first-run-${step.key}`}
                  data-done={false}
                  className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 py-3"
                >
                  <div className="flex min-w-0 items-start gap-3">
                    <span
                      aria-hidden
                      className={cn(
                        'mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full text-xs font-semibold',
                        isNext ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground',
                      )}
                    >
                      {steps.indexOf(step) + 1}
                    </span>
                    <div className="min-w-0">
                      <p className="font-medium">{step.label}</p>
                      <p className="text-sm text-muted-foreground">{step.hint}</p>
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
