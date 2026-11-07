import { useId, useState } from 'react'
import { EXTRA_TIME_STEPS, setStudentExtraTime } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { selectClass } from '@/lib/formClasses'

const LABELS: Record<number, string> = {
  0: 'fără',
  25: '+25 % (un sfert în plus)',
  50: '+50 % (jumătate în plus)',
  100: '+100 % (timp dublu)',
}

/**
 * Extra time on timed tests for one student (CES), on the teacher's student page: saved as soon as it is picked, with
 * a line that says so. It applies to tests the student starts from now on; one already running keeps its clock.
 */
export function ExtraTimeField({ studentId, initial }: { studentId: number; initial: number }) {
  const id = useId()
  const [value, setValue] = useState(initial)
  const [saving, setSaving] = useState(false)
  const [status, setStatus] = useState<{ ok: boolean; text: string } | null>(null)

  async function change(next: number) {
    const before = value
    setValue(next)
    setSaving(true)
    setStatus(null)
    try {
      await setStudentExtraTime(studentId, next)
      setStatus({ ok: true, text: next === 0 ? 'Salvat: fără timp suplimentar.' : 'Salvat. Contează de la următorul test început.' })
    } catch (e) {
      setValue(before) // the select must not show a value the server did not keep
      setStatus({ ok: false, text: errorMessage(e) })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-1" data-testid="student-extra-time">
      <div className="flex flex-wrap items-center gap-2">
        <label htmlFor={id} className="text-muted-foreground">
          Timp suplimentar la testele cu cronometru:
        </label>
        <select
          id={id}
          className={selectClass}
          value={value}
          disabled={saving}
          onChange={(e) => change(Number(e.target.value))}
        >
          {EXTRA_TIME_STEPS.map((step) => (
            <option key={step} value={step}>
              {LABELS[step]}
            </option>
          ))}
        </select>
      </div>
      {status && (
        <p role="status" className={status.ok ? 'text-xs text-muted-foreground' : 'text-xs text-destructive'}>
          {status.text}
        </p>
      )}
    </div>
  )
}
