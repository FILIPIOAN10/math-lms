import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { ErrorState } from '@/components/ErrorState'
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
  setAssignmentOverride,
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
import { PageHeader } from '@/components/PageHeader'
import { RowMenu } from '@/components/RowMenu'
import { FilterBar, FilterSelect, NoMatches } from '@/components/FilterBar'
import { FILTER_FROM, matchesQuery } from '@/lib/search'
import { useConfirm } from '@/components/useConfirm'
import { TestsTabs } from '@/components/TestsTabs'
import { LoadingCards } from '@/components/LoadingCards'
import { selectClass } from '@/lib/formClasses'

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
      /** One student's own deadline: "Termen pentru Ana Pop". */
      studentName?: string
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
    <Dialog
      open={open}
      onOpenChange={(o) => !o && onClose()}
      title={create ? 'Dă o temă' : props.studentName ? `Termen pentru ${props.studentName}` : 'Schimbă termenul'}
    >
      <form className="flex flex-col gap-4" onSubmit={submit}>
        {create && (
          <>
            <div className="flex flex-col gap-2">
              <Label htmlFor="aquiz">Test</Label>
              <select id="aquiz" className={selectClass} value={quizId} onChange={(e) => setQuizId(e.target.value)}>
                <option value="">Alege un test publicat…</option>
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
          {props.mode === 'reschedule' && props.studentName ? (
            <p className="text-xs text-muted-foreground">
              Doar pentru acest elev (de exemplu după o absență). Restul clasei păstrează termenul temei. Predarea lui se
              socotește „cu întârziere” abia după termenul de aici.
            </p>
          ) : (
          <p className="text-xs text-muted-foreground">
            Termenul e „moale”: elevii pot preda și după el, dar tema apare marcată „cu întârziere”. Cu cel mult 24 de ore
            înainte, elevii care nu au predat primesc un email de reamintire (dacă notificările sunt pornite).
          </p>
          )}
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
  const { confirm, dialog: confirmDialog } = useConfirm()
  const [assignments, setAssignments] = useState<AssignmentSummary[]>([])
  const [query, setQuery] = useState('')
  const [stateFilter, setStateFilter] = useState('')
  const [classFilter, setClassFilter] = useState('')
  const [quizzes, setQuizzes] = useState<QuizSummary[]>([])
  const [classes, setClasses] = useState<SchoolClass[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchParams, setSearchParams] = useSearchParams()
  const preselectedQuizId = Number(searchParams.get('quizId')) || null // set by "Dă ca temă" on the quiz list
  const [creating, setCreating] = useState(preselectedQuizId !== null) // arriving from "Dă ca temă" opens the dialog
  const [moving, setMoving] = useState<AssignmentSummary | null>(null)
  // One student's own deadline: the homework and the student whose date is being set.
  const [movingFor, setMovingFor] = useState<{ assignment: AssignmentSummary; student: AssignmentStudentStatus } | null>(null)
  const [openId, setOpenId] = useState<number | null>(null)
  const [status, setStatus] = useState<AssignmentStudentStatus[] | null>(null)
  const [now, setNow] = useState(() => Date.now()) // refreshed whenever the list is (re)loaded
  const [notice, setNotice] = useState<string | null>(null) // "✓ Tema … a fost dată …" after a change
  const [loadError, setLoadError] = useState<string | null>(null)
  const preselectedQuiz = quizzes.find((q) => q.id === preselectedQuizId)
  const linkedId = Number(searchParams.get('open')) || null // a deadline on Acasă opens its hand-in list

  useEffect(() => {
    listAssignments()
      .then((list) => {
        setAssignments(list)
        const linked = list.find((a) => a.id === linkedId)
        if (!linked) return
        getAssignmentStatus(linked.id)
          .then((rows) => {
            setStatus(rows)
            setOpenId(linked.id)
            // A long list would leave the opened homework below the fold.
            requestAnimationFrame(() => document.getElementById(`assignment-${linked.id}`)?.scrollIntoView?.({ block: 'start' }))
          })
          .catch((e) => setLoadError(errorMessage(e)))
      })
      .catch((e) => setLoadError(errorMessage(e)))
      .finally(() => setLoading(false))
    listQuizzes().then(setQuizzes).catch((e) => setLoadError(errorMessage(e)))
    listClasses().then(setClasses).catch((e) => setLoadError(errorMessage(e)))
  }, [linkedId])

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

  /** One student's exception; the answer is the refreshed hand-in list, and the counts on the card follow. */
  async function setOverride(a: AssignmentSummary, s: AssignmentStudentStatus, dueAt: string | null, excused: boolean,
                             done: string) {
    setError(null)
    try {
      setStatus(await setAssignmentOverride(a.id, s.studentId, { dueAt, excused }))
      setNotice(done)
      await reload()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  function studentActions(a: AssignmentSummary, s: AssignmentStudentStatus) {
    const items = [
      { label: s.ownDueAt ? 'Schimbă termenul lui/ei' : 'Termen doar pentru el/ea', onClick: () => setMovingFor({ assignment: a, student: s }) },
      s.excused
        ? { label: 'Anulează scutirea', onClick: () => void setOverride(a, s, s.ownDueAt, false, `${s.fullName} are din nou tema.`) }
        : { label: 'Scutește de temă', onClick: () => void setOverride(a, s, s.ownDueAt, true, `${s.fullName} e scutit(ă) de tema „${a.quizTitle}”.`) },
    ]
    if (s.ownDueAt && !s.excused) {
      items.push({ label: 'Înapoi la termenul clasei', onClick: () => void setOverride(a, s, null, false, `${s.fullName} are din nou termenul clasei.`) })
    }
    return items
  }

  async function remove(a: AssignmentSummary) {
    const yes = await confirm({
      title: `Ștergi tema „${a.quizTitle}”?`,
      body: `Tema dispare pentru ${a.schoolClassName}. Lucrările deja predate rămân.`,
      confirmLabel: 'Șterge',
      destructive: true,
    })
    if (!yes) return
    setError(null)
    try {
      await deleteAssignment(a.id)
      if (openId === a.id) setOpenId(null)
      await reload()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  const filtering = query.trim() !== '' || stateFilter !== '' || classFilter !== ''
  const classNames = [...new Set(assignments.map((a) => a.schoolClassName))]
    .sort((a, b) => a.localeCompare(b, 'ro', { numeric: true }))
  const visibleAssignments = assignments.filter((a) =>
    matchesQuery(query, a.quizTitle, a.schoolClassName)
    && (stateFilter === '' || (stateFilter === 'overdue') === a.overdue)
    && (classFilter === '' || a.schoolClassName === classFilter))
  const clearFilters = () => {
    setQuery('')
    setStateFilter('')
    setClassFilter('')
  }

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-4">
        {confirmDialog}
        <PageHeader
          title="Teme"
          description="Dai un test publicat unei clase, cu un termen. Vezi cine a predat, cine întârzie și cine nu a început."
          actions={<Button onClick={() => setCreating(true)} data-testid="assignment-new">Dă o temă</Button>}
        />
        <TestsTabs current="assignments" />

        {notice && (
          <p role="status" className="rounded-lg border border-emerald-500/40 bg-emerald-500/10 px-3 py-2 text-sm text-emerald-800 dark:text-emerald-200">
            ✓ {notice}
          </p>
        )}
        {loadError && <ErrorState message={loadError} />}
        {error && <p className="text-sm text-destructive">{error}</p>}
        {loading && <LoadingCards />}
        {!loading && assignments.length === 0 && (
          <p className="text-sm text-muted-foreground">Nicio temă încă. Apasă „Dă o temă”.</p>
        )}
        {assignments.length >= FILTER_FROM && (
          <FilterBar
            label="Caută temă sau clasă"
            search={query}
            onSearch={setQuery}
            active={filtering}
            onClear={clearFilters}
            shown={visibleAssignments.length}
            total={assignments.length}
          >
            <FilterSelect
              label="Termen"
              value={stateFilter}
              onChange={setStateFilter}
              options={[{ value: '', label: 'Toate' }, { value: 'open', label: 'Deschise' }, { value: 'overdue', label: 'Cu termen depășit' }]}
            />
            {classNames.length > 1 && (
              <FilterSelect
                label="Clasa"
                value={classFilter}
                onChange={setClassFilter}
                options={[{ value: '', label: 'Toate clasele' }, ...classNames.map((name) => ({ value: name, label: name }))]}
              />
            )}
          </FilterBar>
        )}
        {assignments.length > 0 && visibleAssignments.length === 0 && <NoMatches onClear={clearFilters} />}

        {visibleAssignments.map((a) => (
          <Card key={a.id} id={`assignment-${a.id}`} className="scroll-mt-20">
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
                    {a.excused > 0 && <> · scutiți {a.excused}</>}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <Button size="sm" variant="outline" onClick={() => toggleStatus(a)}>
                    {openId === a.id ? 'Ascunde' : 'Cine a predat'}
                  </Button>
                  <RowMenu
                    label={`Mai multe acțiuni pentru ${a.quizTitle}`}
                    items={[
                      { label: 'Schimbă termenul', onClick: () => setMoving(a) },
                      { label: 'Șterge tema', destructive: true, onClick: () => void remove(a) },
                    ]}
                  />
                </div>
              </div>

              {openId === a.id && status && (
                <table className="w-full text-sm" data-testid="assignment-status">
                  <thead className="text-left text-muted-foreground">
                    <tr><th className="py-1">Elev</th><th>Stare</th><th>Predat</th><th>Notă</th><th><span className="sr-only">Excepții</span></th></tr>
                  </thead>
                  <tbody>
                    {status.map((s) => (
                      <tr key={s.studentId} className="border-t">
                        <td className="py-1">
                          <Link to={`/admin/students/${s.studentId}`} className="hover:underline">{s.fullName}</Link>
                          {s.ownDueAt && !s.excused && (
                            <span data-testid="own-due" className="block text-xs text-muted-foreground">
                              termen propriu: {formatDate(s.ownDueAt)}
                            </span>
                          )}
                        </td>
                        <td>{s.excused ? <span className="font-medium text-sky-700 dark:text-sky-300">Scutit</span> : STATE_LABEL[s.state]}</td>
                        <td>
                          {s.submittedAt ? formatDate(s.submittedAt) : '—'}
                          {s.late && <span className="ml-1 text-amber-700 dark:text-amber-300">(cu întârziere)</span>}
                        </td>
                        <td>{s.score ?? '—'}</td>
                        <td className="text-right">
                          <RowMenu label={`Excepții pentru ${s.fullName}`} items={studentActions(a, s)} />
                        </td>
                      </tr>
                    ))}
                    {status.length === 0 && (
                      <tr><td colSpan={5} className="py-2 text-muted-foreground">Niciun elev înscris în această clasă.</td></tr>
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
            const created = await createAssignment(input)
            setNotice(`Tema „${created.quizTitle}” a fost dată clasei ${created.schoolClassName}, termen ${formatDate(created.dueAt)}.`)
            await reload()
          }}
        />
      )}
      {movingFor && (
        <AssignmentDialog
          open
          mode="reschedule"
          studentName={movingFor.student.fullName}
          currentDueAt={movingFor.student.ownDueAt ?? movingFor.assignment.dueAt}
          onClose={() => setMovingFor(null)}
          onSubmit={async (input) => {
            const { assignment, student } = movingFor
            // Through the dialog's own error line: a refused date keeps the dialog open.
            setStatus(await setAssignmentOverride(assignment.id, student.studentId, { dueAt: input.dueAt, excused: student.excused }))
            setNotice(`${student.fullName} are termen propriu: ${formatDate(input.dueAt)}.`)
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
            const moved = await rescheduleAssignment(moving.id, input.dueAt)
            setNotice(`Termenul temei „${moved.quizTitle}” e acum ${formatDate(moved.dueAt)}.`)
            await reload()
          }}
        />
      )}
    </div>
  )
}
