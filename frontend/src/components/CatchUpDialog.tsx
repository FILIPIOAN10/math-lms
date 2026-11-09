import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { AssignmentSummary, ClassMastery, QuizSummary } from '@/lib/api'
import { localInputToIso } from '@/lib/assignments'
import { catchUpQuizzes, defaultCatchUpDue, weakStudentIds } from '@/lib/catchUp'
import { errorMessage } from '@/lib/errors'
import { roCount } from '@/lib/format'
import { selectClass } from '@/lib/formClasses'

/**
 * Catch-up homework from the mastery map: one chapter (a column of the grid), the students weak there already ticked,
 * the chapter's own quiz chosen when it has one, a deadline a week out. Only the ticked students get the homework - the
 * rest of the class never sees it. The API call is passed in, so the dialog is tested without the network.
 */
export function CatchUpDialog({
  open,
  onClose,
  grid,
  column,
  classId,
  quizzes,
  givenQuizIds = [],
  onCreated,
  createCatchUpAssignment,
}: {
  open: boolean
  onClose: () => void
  grid: ClassMastery
  column: number
  classId: number
  quizzes: QuizSummary[]
  /** Quizzes already homework for this class: one quiz goes to a class once, so these are shown but cannot be picked. */
  givenQuizIds?: number[]
  onCreated: (created: AssignmentSummary) => void
  createCatchUpAssignment: (input: {
    quizId: number; schoolClassId: number; studentIds: number[]; dueAt: string
  }) => Promise<AssignmentSummary>
}) {
  const chapter = grid.chapters[column]
  const choices = catchUpQuizzes(quizzes, chapter.id, classId)
  const [studentIds, setStudentIds] = useState<number[]>(() => weakStudentIds(grid, column))
  const [quizId, setQuizId] = useState(() => {
    const first = choices.chapter.find((q) => !givenQuizIds.includes(q.id))
    return first ? String(first.id) : ''
  })
  const [due, setDue] = useState(() => defaultCatchUpDue())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function toggle(id: number) {
    setStudentIds((prev) => (prev.includes(id) ? prev.filter((s) => s !== id) : [...prev, id]))
  }

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
      // In the order the students are listed, whatever order they were ticked in.
      const chosen = grid.students.map((s) => s.studentId).filter((id) => studentIds.includes(id))
      onCreated(await createCatchUpAssignment({ quizId: Number(quizId), schoolClassId: classId, studentIds: chosen, dueAt: iso }))
      onClose()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const noQuiz = choices.chapter.length === 0 && choices.other.length === 0
  const given = (q: QuizSummary) => givenQuizIds.includes(q.id)
  const option = (q: QuizSummary) => (
    <option key={q.id} value={q.id} disabled={given(q)}>
      {given(q) ? `${q.title} (deja temă clasei)` : q.title}
    </option>
  )

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()} title={`Temă de recuperare — ${chapter.title}`}>
      <form className="flex flex-col gap-4" onSubmit={submit}>
        <p className="text-sm text-muted-foreground">
          Doar elevii bifați primesc tema; restul clasei nu o vede. Sunt bifați deja cei sub 50% la acest capitol.
        </p>
        <fieldset className="flex flex-col gap-1">
          <legend className="mb-2 text-sm font-medium">Elevi</legend>
          {grid.students.map((s) => {
            const percent = s.cells[column]?.percent ?? null
            const label = `${s.fullName} (${percent === null ? 'fără date' : `${percent}%`})`
            return (
              <label key={s.studentId} className="flex min-h-11 items-center gap-2 text-sm">
                <input type="checkbox" className="size-4" checked={studentIds.includes(s.studentId)}
                       onChange={() => toggle(s.studentId)} aria-label={label} />
                {label}
              </label>
            )
          })}
        </fieldset>
        <div className="flex flex-col gap-2">
          <Label htmlFor="cquiz">Test</Label>
          <select id="cquiz" className={selectClass} value={quizId} onChange={(e) => setQuizId(e.target.value)}>
            <option value="">Alege un test publicat…</option>
            {choices.chapter.length > 0 && (
              <optgroup label={`Legate de „${chapter.title}”`}>
                {choices.chapter.map(option)}
              </optgroup>
            )}
            {choices.other.length > 0 && (
              <optgroup label="Alte teste">
                {choices.other.map(option)}
              </optgroup>
            )}
          </select>
          {noQuiz && (
            <p className="text-xs text-muted-foreground">Niciun test publicat pentru această clasă. Publică unul întâi.</p>
          )}
          {choices.chapter.some(given) && (
            <p className="text-xs text-muted-foreground">
              Un test e dat ca temă unei clase o singură dată. Pentru unul „deja temă clasei” folosește ⋯ „Excepții” la
              Teme (termen nou pentru un elev), sau alege alt test.
            </p>
          )}
          {!noQuiz && choices.chapter.length === 0 && (
            <p className="text-xs text-muted-foreground">
              Niciun test publicat nu e legat de acest capitol; poți alege altul sau crea unul din exercițiile capitolului.
            </p>
          )}
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="cdue">Termen</Label>
          <Input id="cdue" type="datetime-local" value={due} onChange={(e) => setDue(e.target.value)} />
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Anulează</Button>
          <Button type="submit" disabled={busy || quizId === '' || due === '' || studentIds.length === 0}>
            {`Dă tema (${roCount(studentIds.length, 'elev', 'elevi')})`}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
