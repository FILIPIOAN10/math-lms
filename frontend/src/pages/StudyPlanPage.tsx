import { useEffect, useId, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ErrorState } from '@/components/ErrorState'
import { LoadingCards } from '@/components/LoadingCards'
import { PageHeader } from '@/components/PageHeader'
import { HomeSection } from '@/components/home/HomeBits'
import { StudyPlanView } from '@/components/StudyPlanView'
import { getMyStudyPlan, setMyEnGoal, type StudyPlan } from '@/lib/api'
import { formatGrade } from '@/lib/enPrediction'
import { errorMessage } from '@/lib/errors'
import { parseTarget, TARGET_CHOICES } from '@/lib/studyPlan'

/**
 * „Planul până la EN", the student's own: they pick the grade they aim for (a tap on 8,00 - 10, or any other typed),
 * and the page shows where they stand, what to do today, the estimate day by day against the target and the weeks up
 * to the exam - all worked out by the server from their answers, so it moves as they practise.
 */
export function StudyPlanPage() {
  const [plan, setPlan] = useState<StudyPlan | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getMyStudyPlan()
      .then((p) => !cancelled && setPlan(p))
      .catch((e) => !cancelled && setLoadError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="p-4">
      <div className="mx-auto max-w-4xl space-y-4">
        <PageHeader title="Planul până la EN" description="Nota pe care o vrei și ce ai de lucrat, săptămână de săptămână." />
        {loadError && <ErrorState message={loadError} />}
        {!plan && !loadError && <LoadingCards />}
        {plan && (
          <>
            <TargetPicker target={plan.target} onSaved={setPlan} />
            <StudyPlanView plan={plan} own />
          </>
        )}
      </div>
    </div>
  )
}

function TargetPicker({ target, onSaved }: { target: number | null; onSaved: (plan: StudyPlan) => void }) {
  const inputId = useId()
  const [typed, setTyped] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function save(value: number) {
    setBusy(true)
    setError(null)
    try {
      onSaved(await setMyEnGoal(value))
      setTyped('')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  function submitTyped(e: FormEvent) {
    e.preventDefault()
    const value = parseTarget(typed)
    if (value === null) {
      setError('Scrie o notă de la 1 la 10, cu cel mult două zecimale (de exemplu 8,75).')
      return
    }
    void save(value)
  }

  return (
    <HomeSection title="Ținta ta">
      <Card>
        <CardContent className="space-y-3 py-4">
          <div role="group" aria-label="Alege ținta" className="flex flex-wrap gap-2">
            {TARGET_CHOICES.map((choice) => (
              <Button key={choice} variant={target === choice ? 'default' : 'outline'} aria-pressed={target === choice}
                      disabled={busy} onClick={() => void save(choice)}>
                {formatGrade(choice)}
              </Button>
            ))}
          </div>
          <form onSubmit={submitTyped} className="flex flex-wrap items-end gap-2">
            <div className="space-y-1">
              <Label htmlFor={inputId}>Altă notă</Label>
              <Input id={inputId} inputMode="decimal" placeholder="ex. 8,75" value={typed} className="w-32"
                     onChange={(e) => setTyped(e.target.value)} />
            </div>
            <Button type="submit" variant="outline" disabled={busy || typed.trim() === ''}>Salvează ținta</Button>
          </form>
          {target !== null && !TARGET_CHOICES.includes(target) && (
            <p className="text-sm text-muted-foreground">Ținta ta acum: {formatGrade(target)}</p>
          )}
          {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        </CardContent>
      </Card>
    </HomeSection>
  )
}
