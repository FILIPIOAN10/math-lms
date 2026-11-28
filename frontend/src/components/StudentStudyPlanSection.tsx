import { useEffect, useId, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { StudyPlanView } from '@/components/StudyPlanView'
import {
  getStudentStudyPlan,
  listEnChapters,
  pinStudyPlanChapter,
  unpinStudyPlanChapter,
  type EnChapterSummary,
  type StudyPlan,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'

/**
 * „Planul până la EN" of one student, on the teacher's student page: the plan as the student sees it (read only), and
 * the chapters the teacher put into it - first in the plan, whatever their gain - with a way to add or take one out.
 * A plan that cannot be read shows nothing (the rest of the page works without it).
 */
export function StudentStudyPlanSection({ studentId }: { studentId: number }) {
  const selectId = useId()
  const [plan, setPlan] = useState<StudyPlan | null>(null)
  const [chapters, setChapters] = useState<EnChapterSummary[]>([])
  const [chosen, setChosen] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getStudentStudyPlan(studentId)
      .then((p) => !cancelled && setPlan(p))
      .catch(() => undefined)
    listEnChapters()
      .then((list) => !cancelled && setChapters(list))
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [studentId])

  async function run(action: () => Promise<StudyPlan>) {
    setBusy(true)
    setError(null)
    try {
      setPlan(await action())
      setChosen('')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  if (!plan) return null

  const pinned = new Set(plan.pins.map((p) => p.chapterId))
  // Whole chapters only: a section or point is opened from its chapter's page.
  const offered = chapters.filter((c) => c.parentId === null && !pinned.has(c.id))

  return (
    <section aria-labelledby={selectId + '-title'} className="space-y-2">
      <h2 id={selectId + '-title'} className="font-medium">Planul până la EN</h2>
      <Card data-testid="study-plan-pins">
        <CardContent className="space-y-3 py-4">
          <p className="text-sm font-medium">Capitole puse de tine în plan</p>
          {plan.pins.length === 0 ? (
            <p className="text-sm text-muted-foreground">Niciun capitol pus de tine.</p>
          ) : (
            <ul className="flex flex-wrap gap-2">
              {plan.pins.map((p) => (
                <li key={p.chapterId} className="inline-flex items-center gap-1 rounded-full border py-0.5 pr-1 pl-3 text-sm">
                  <span className="font-semibold">{p.code}</span> {p.title}
                  <Button variant="ghost" size="xs" disabled={busy} aria-label={`Scoate ${p.code} din plan`}
                          onClick={() => void run(() => unpinStudyPlanChapter(studentId, p.chapterId))}>
                    Scoate
                  </Button>
                </li>
              ))}
            </ul>
          )}
          <div className="flex flex-wrap items-end gap-2">
            <div className="space-y-1">
              <Label htmlFor={selectId}>Capitol de pus în plan</Label>
              <select id={selectId} value={chosen} onChange={(e) => setChosen(e.target.value)}
                      className="h-8 rounded-lg border border-input bg-background px-2 text-sm max-sm:h-11">
                <option value="">Alege un capitol…</option>
                {offered.map((c) => (
                  <option key={c.id} value={c.id}>{c.code} · {c.title}</option>
                ))}
              </select>
            </div>
            <Button variant="outline" disabled={busy || chosen === ''}
                    onClick={() => void run(() => pinStudyPlanChapter(studentId, Number(chosen)))}>
              Pune în plan
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">
            Un capitol pus de tine vine primul în planul elevului, oricât ar adăuga la nota estimată.
          </p>
          {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        </CardContent>
      </Card>
      <StudyPlanView plan={plan} own={false} />
    </section>
  )
}
