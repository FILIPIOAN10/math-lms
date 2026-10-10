import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  createAssignment,
  deleteAssignment,
  getAssignmentStatus,
  listAssignments,
  listClasses,
  listQuizzes,
  rescheduleAssignment,
  type AssignmentInput,
  type AssignmentState,
  type AssignmentStudentStatus,
  type AssignmentSummary,
  type QuizSummary,
  type SchoolClass,
} from '@/lib/api'
import { dueCountdown, isoToLocalInput, localInputToIso } from '@/lib/assignments'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'

const selectClass =
  'h-9 rounded-lg border border-border bg-background px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50'

const STATE_LABEL: Record<AssignmentState, string> = {
  NOT_STARTED: 'Neînceput',
  IN_PROGRESS: 'În lucru',
  SUBMITTED: 'Predat — în corectare',
  GRADED: 'Predat și notat',
}

type DialogProps =
  | {
      mode: 'create'; quizzes: QuizSummary[]; classes: SchoolClass[]; currentDueAt?: undefined
      initialQuizId?: number | null; initialClassId?: number | null // "Dă ca temă" from the quiz list
    }
  | {
      mode: 'reschedule'; currentDueAt: string; quizzes?: undefined; classes?: undefined
      initialQuizId?: undefined; initialClassId?: undefined
    }

/**
 * Create a homework (pick a published quiz, a class and a deadline) or just move the deadline of an existing one.
 * The deadline is checked here first so a typo is caught before the round trip; the server checks it again.
 */
export function AssignmentDialog({
  open,
  onClose,
  onSubmit,
  ...props
}: { open: boolean; onClose: () => void; onSubmit: (input: AssignmentInput) => Promise<void> } & DialogProps) {
  const [quizId, setQuizId] = useState(props.initialQuizId ? String(props.initialQuizId) : '')
  const [classId, setClassId] = useState(props.initialClassId ? String(props.initialClassId) : '')
  const [due, setDue] = useState(props.mode === 'reschedule' ? isoToLocalInput(props.currentDueAt) : '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const create = props.mode === 'create'
  const ready = due !== '' && (!create || (quizId !== '' && classId !== ''))

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const iso = localInputToIso(due)
    if (Date.parse(iso) <= Date.now()) {
      setError('Termenul trebuie să fie în viitor.')
      return
    }
    setBusy(true)
    try {
      await onSubmit({ quizId: Number(quizId), schoolClassId: Number(classId), dueAt: iso })
      onClose()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()} title={create ? 'Dă o temă' : 'Schimbă termenul'}>
      <form className="flex flex-col gap-4" onSubmit={submit}>
        {create && (
          <>
            <div className="flex flex-col gap-2">
              <Label htmlFor="aquiz">Quiz</Label>
              <select id="aquiz" className={selectClass} value={quizId} onChange={(e) => setQuizId(e.target.value)}>
                <option value="">Alege un quiz publicat…</option>
                {props.quizzes.filter((q) => q.status === 'PUBLISHED').map((q) => (
                  <option key={q.id} value={q.id}>{q.title}</option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="aclass">Clasa</Label>
              <select id="aclass" className={selectClass} value={classId} onChange={(e) => setClassId(e.target.value)}>
                <option value="">Alege clasa…</option>
                {props.classes.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </div>
          </>
        )}
        <div className="flex flex-col gap-2">
          <Label htmlFor="adue">Termen</Label>
          <Input id="adue" type="datetime-local" value={due} onChange={(e) => setDue(e.target.value)} />
          <p className="text-xs text-muted-foreground">
            Termenul e „moale”: elevii pot preda și după el, dar tema apare marcată „cu întârziere”. Cu cel mult 24 de ore
            înainte, elevii care nu au predat primesc un email de reamintire (dacă notificările sunt pornite).
          </p>
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Anulează</Button>
          <Button type="submit" disabled={busy || !ready}>{create ? 'Dă tema' : 'Salvează'}</Button>
        </div>
      </form>
    </Dialog>
  )
}

/** The teacher's homework page: assign a quiz to a class with a deadline, see who handed in, move or remove it. */
export function AdminAssignmentsPage() {
  const [assignments, setAssignments] = useState<AssignmentSummary[]>([])
  const [quizzes, setQuizzes] = useState<QuizSummary[]>([])
  const [classes, setClasses] = useState<SchoolClass[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchParams, setSearchParams] = useSearchParams()
  const preselectedQuizId = Number(searchParams.get('quizId')) || null // set by "Dă ca temă" on the quiz list
  const [creating, setCreating] = useState(preselectedQuizId !== null) // arriving from "Dă ca temă" opens the dialog
  const [moving, setMoving] = useState<AssignmentSummary | null>(null)
  const [openId, setOpenId] = useState<number | null>(null)
  const [status, setStatus] = useState<AssignmentStudentStatus[] | null>(null)
  const [now, setNow] = useState(() => Date.now()) // refreshed whenever the list is (re)loaded
  const preselectedQuiz = quizzes.find((q) => q.id === preselectedQuizId)

  useEffect(() => {
    listAssignments()
      .then(setAssignments)
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setLoading(false))
    listQuizzes().then(setQuizzes).catch((e) => setError(errorMessage(e)))
    listClasses().then(setClasses).catch((e) => setError(errorMessage(e)))
  }, [])

  const reload = () =>
    listAssignments().then((list) => {
      setAssignments(list)
      setNow(Date.now())
    })

  async function toggleStatus(a: AssignmentSummary) {
    if (openId === a.id) {
      setOpenId(null)
      return
    }
    setError(null)
    try {
      setStatus(await getAssignmentStatus(a.id))
      setOpenId(a.id)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  async function remove(a: AssignmentSummary) {
    if (!window.confirm(`Sigur ștergi tema „${a.quizTitle}” pentru ${a.schoolClassName}?`)) return
    setError(null)
    try {
      await deleteAssignment(a.id)
      if (openId === a.id) setOpenId(null)
      await reload()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        <div className="flex items-center justify-between gap-2">
          <h1 className="text-2xl font-semibold">Teme</h1>
          <div className="flex gap-2">
            <Button onClick={() => setCreating(true)} data-testid="assignment-new">Dă o temă</Button>
          </div>
        </div>
        <p className="text-sm text-muted-foreground">
          Dai un quiz publicat unei clase, cu un termen. Vezi cine a predat, cine întârzie și cine nu a început.
        </p>

        {error && <p className="text-sm text-destructive">{error}</p>}
        {loading && <p className="text-muted-foreground">Se încarcă...</p>}
        {!loading && assignments.length === 0 && (
          <p className="text-sm text-muted-foreground">Nicio temă încă. Apasă „Dă o temă”.</p>
        )}

        {assignments.map((a) => (
          <Card key={a.id}>
            <CardContent data-testid="assignment-row" className="space-y-3 py-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="min-w-0">
                  <p className="truncate font-medium">
                    {a.quizTitle} <span className="font-normal text-muted-foreground">· {a.schoolClassName}</span>
                  </p>
                  <p className="text-sm text-muted-foreground">
                    Termen: {formatDate(a.dueAt)} ({dueCountdown(a.dueAt, now)})
                    {a.overdue && <span className="ml-2 font-medium text-destructive">termen depășit</span>}
                  </p>
                  <p className="text-sm" data-testid="assignment-counts">
                    Predate {a.done}/{a.enrolled}
                    {a.late > 0 && <> · cu întârziere {a.late}</>}
                    {a.inProgress > 0 && <> · în lucru {a.inProgress}</>}
                    {a.notStarted > 0 && <> · neîncepute {a.notStarted}</>}
                  </p>
                </div>
                <div className="flex gap-2">
                  <Button size="sm" variant="outline" onClick={() => toggleStatus(a)}>
                    {openId === a.id ? 'Ascunde' : 'Stare'}
                  </Button>
                  <Button size="sm" variant="outline" onClick={() => setMoving(a)}>Termen</Button>
                  <Button size="sm" variant="outline" onClick={() => remove(a)} className="text-destructive">Șterge</Button>
                </div>
              </div>

              {openId === a.id && status && (
                <table className="w-full text-sm" data-testid="assignment-status">
                  <thead className="text-left text-muted-foreground">
                    <tr><th className="py-1">Elev</th><th>Stare</th><th>Predat</th><th>Notă</th></tr>
                  </thead>
                  <tbody>
                    {status.map((s) => (
                      <tr key={s.studentId} className="border-t">
                        <td className="py-1">{s.fullName}</td>
                        <td>{STATE_LABEL[s.state]}</td>
                        <td>
                          {s.submittedAt ? formatDate(s.submittedAt) : '—'}
                          {s.late && <span className="ml-1 text-amber-700 dark:text-amber-300">(cu întârziere)</span>}
                        </td>
                        <td>{s.score ?? '—'}</td>
                      </tr>
                    ))}
                    {status.length === 0 && (
                      <tr><td colSpan={4} className="py-2 text-muted-foreground">Niciun elev înscris în această clasă.</td></tr>
                    )}
                  </tbody>
                </table>
              )}
            </CardContent>
          </Card>
        ))}
      </div>

      {creating && (preselectedQuizId === null || preselectedQuiz) && (
        <AssignmentDialog
          open
          mode="create"
          quizzes={quizzes}
          classes={classes}
          initialQuizId={preselectedQuizId}
          initialClassId={preselectedQuiz?.schoolClassId ?? null} // a quiz reserved for a class can only go to that class
          onClose={() => {
            setCreating(false)
            if (preselectedQuizId) setSearchParams({}, { replace: true })
          }}
          onSubmit={async (input) => {
            await createAssignment(input)
            await reload()
          }}
        />
      )}
      {moving && (
        <AssignmentDialog
          open
          mode="reschedule"
          currentDueAt={moving.dueAt}
          onClose={() => setMoving(null)}
          onSubmit={async (input) => {
            await rescheduleAssignment(moving.id, input.dueAt)
            await reload()
          }}
        />
      )}
    </div>
  )
}
