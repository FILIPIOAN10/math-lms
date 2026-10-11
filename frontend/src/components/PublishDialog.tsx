import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  publishAndAssign as publishAndAssignRequest,
  setQuizPublished as setQuizPublishedRequest,
  type AssignmentSummary,
  type SchoolClass,
} from '@/lib/api'
import { isoToLocalInput, localInputToIso } from '@/lib/assignments'
import { errorMessage } from '@/lib/errors'

const selectClass =
  'h-9 rounded-lg border border-border bg-background px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50'

/** A week from now at 20:00 — the usual "for next lesson" deadline, easy to change. */
function defaultDue(nowMs: number): string {
  const d = new Date(nowMs + 7 * 86_400_000)
  d.setHours(20, 0, 0, 0)
  return isoToLocalInput(d.toISOString())
}

interface Props {
  open: boolean
  onClose: () => void
  quiz: { id: number; title: string }
  /** The class the quiz is reserved for (it can only be homework there), or null for a quiz for every student. */
  quizClassId: number | null
  classes: SchoolClass[]
  /** Called once published, with the homework given at the same time (null = published only). */
  onPublished: (assigned: AssignmentSummary | null) => Promise<void> | void
  /** Injected in tests; default to the real API calls. */
  publishAndAssign?: typeof publishAndAssignRequest
  setQuizPublished?: typeof setQuizPublishedRequest
}

/**
 * T3: "Publică" opens this instead of publishing straight away, so a test becomes homework in the same step:
 * "Publică și dă ca temă: [clasa] până la [termen]". Unticking "Dă-l și ca temă" just publishes. Publishing and
 * assigning are one request on the server — all or nothing.
 */
export function PublishDialog({
  open,
  onClose,
  quiz,
  quizClassId,
  classes,
  onPublished,
  publishAndAssign = publishAndAssignRequest,
  setQuizPublished = setQuizPublishedRequest,
}: Props) {
  const [assign, setAssign] = useState(classes.length > 0)
  const [classId, setClassId] = useState(quizClassId !== null ? String(quizClassId) : '')
  const [due, setDue] = useState(() => defaultDue(Date.now()))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const ready = !assign || (classId !== '' && due !== '')

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (assign) {
      if (Date.parse(localInputToIso(due)) <= Date.now()) {
        setError('Termenul trebuie să fie în viitor.')
        return
      }
    }
    setBusy(true)
    try {
      let assigned: AssignmentSummary | null = null
      if (assign) {
        assigned = await publishAndAssign(quiz.id, { schoolClassId: Number(classId), dueAt: localInputToIso(due) })
      } else {
        await setQuizPublished(quiz.id, true)
      }
      await onPublished(assigned)
      onClose()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => !o && onClose()}
      title={`Publică „${quiz.title}”`}
      description="După publicare, elevii îl văd la „Teste disponibile”."
    >
      <form className="flex flex-col gap-4" onSubmit={submit}>
        <label className="flex items-center gap-2 text-sm font-medium">
          <input type="checkbox" checked={assign} disabled={classes.length === 0} onChange={(e) => setAssign(e.target.checked)} />
          Dă-l și ca temă
        </label>
        {classes.length === 0 && (
          <p className="text-sm text-muted-foreground">Nu ai încă nicio clasă — îl poți da ca temă după ce creezi una.</p>
        )}

        {assign && (
          <>
            <div className="flex flex-col gap-2">
              <Label htmlFor="publish-class">Clasa</Label>
              <select
                id="publish-class"
                className={selectClass}
                value={classId}
                disabled={quizClassId !== null} // a quiz reserved for a class can only be homework there
                onChange={(e) => setClassId(e.target.value)}
              >
                <option value="">Alege clasa…</option>
                {classes.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="publish-due">Termen</Label>
              <Input id="publish-due" type="datetime-local" value={due} onChange={(e) => setDue(e.target.value)} />
              <p className="text-xs text-muted-foreground">
                Termen „moale”: se poate preda și după, marcat „cu întârziere”.
              </p>
            </div>
          </>
        )}

        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Anulează</Button>
          <Button type="submit" disabled={busy || !ready}>
            {busy ? 'Se publică…' : assign ? 'Publică și dă tema' : 'Publică'}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
