import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ErrorState } from '@/components/ErrorState'
import { LoadingCards } from '@/components/LoadingCards'
import { PageHeader } from '@/components/PageHeader'
import { StudyPlanView } from '@/components/StudyPlanView'
import { getChildStudyPlan, listMyChildren, type StudyPlan } from '@/lib/api'
import { errorMessage } from '@/lib/errors'

/**
 * „Planul până la EN" of a parent's child, to read: the target the child picked, where they stand (and whether they
 * are behind), the estimate day by day and the weeks. No buttons: the target is the child's to pick.
 */
export function ParentStudyPlanPage() {
  const childId = Number(useParams<{ childId: string }>().childId)
  const [plan, setPlan] = useState<StudyPlan | null>(null)
  const [childName, setChildName] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getChildStudyPlan(childId)
      .then((p) => !cancelled && setPlan(p))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    // Only for the title: the page still works without the name.
    listMyChildren()
      .then((children) => !cancelled && setChildName(children.find((c) => c.id === childId)?.fullName ?? null))
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [childId])

  const name = childName ?? 'Copilul meu'
  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <PageHeader
          title={`Planul EN — ${name}`}
          description="Ținta aleasă de copil și ce are de lucrat până la examen, săptămână de săptămână."
          breadcrumbs={[{ label: 'Acasă', to: '/' }, { label: name, to: `/parent/children/${childId}` }, { label: 'Planul EN' }]}
        />
        {error && <ErrorState message={error} />}
        {!plan && !error && <LoadingCards />}
        {plan && <StudyPlanView plan={plan} own={false} />}
      </div>
    </div>
  )
}
