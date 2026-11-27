import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { EnClassMastery } from '@/lib/api'
import { localInputToIso } from '@/lib/assignments'
import { defaultCatchUpDue } from '@/lib/catchUp'
import { errorMessage } from '@/lib/errors'
import { roCount } from '@/lib/format'
import { weakEnStudentIds } from '@/lib/recap'

/**
 * Catch-up homework from the collection, started from the students' mistakes: one EN chapter (a column of the EN grid),
 * the students below 50% there already ticked, a few approved „Teme recapitulative” exercises of that chapter (the ones
 * the class has not had first). One click makes the test, publishes it for this class and gives it to the ticked
 * students only. The API call is passed in, so the dialog is tested without the network.
 */
export function RecapCatchUpDialog({ grid, column, classId, approved, onClose, onCreated, createRecapCatchUp }: {
  grid: EnClassMastery
  column: number
  classId: number
  /** Approved exercises of this chapter in the collection. */
  approved: number
  onClose: () => void
  onCreated: (text: string) => void
  createRecapCatchUp: (input: {
    schoolClassId: number; enChapterId: number; studentIds: number[]; count: number; dueAt: string
  }) => Promise<{ quizId: number; assignmentId: number; exerciseCount: number }>
}) {
  const chapter = grid.chapters[column]
  const [studentIds, setStudentIds] = useState<number[]>(() => weakEnStudentIds(grid, column))
  const [count, setCount] = useState(() => String(Math.min(3, Math.max(1, approved))))
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
      const chosen = grid.students.map((s) => s.studentId).filter((id) => studentIds.includes(id))
      const result = await createRecapCatchUp({
        schoolClassId: classId, enChapterId: chapter.id, studentIds: chosen, count: Number(count), dueAt: iso,
      })
      onCreated(`Am dat tema din culegere (${roCount(result.exerciseCount, 'exercițiu', 'exerciții')}, ${chapter.code}) la `
        + `${roCount(chosen.length, 'elev', 'elevi')}.`)
      onClose()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={`Temă din culegere — ${chapter.code} ${chapter.title}`}>
      <form className="flex flex-col gap-4" onSubmit={submit}>
        {approved === 0 ? (
          <p className="text-sm text-muted-foreground">
            Niciun exercițiu aprobat din culegere la acest capitol. Aprobă câteva în pagina Culegere, apoi revino.
          </p>
        ) : (
          <p className="text-sm text-muted-foreground">
            Fac un test din exercițiile aprobate ale capitolului ({approved} în culegere; întâi cele pe care clasa nu le-a
            avut), îl public pentru clasă și îl dau doar elevilor bifați. Sunt bifați cei sub 50% aici.
          </p>
        )}
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
          <Label htmlFor="rcount">Câte exerciții</Label>
          <Input id="rcount" type="number" min={1} max={Math.max(1, Math.min(10, approved))} value={count}
                 onChange={(e) => setCount(e.target.value)} disabled={approved === 0} />
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="rdue">Termen</Label>
          <Input id="rdue" type="datetime-local" value={due} onChange={(e) => setDue(e.target.value)} />
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Anulează</Button>
          <Button type="submit"
                  disabled={busy || approved === 0 || studentIds.length === 0 || due === '' || !(Number(count) >= 1)}>
            {`Dă tema (${roCount(studentIds.length, 'elev', 'elevi')})`}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
