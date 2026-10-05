import { type StudentAssignment } from '@/lib/api'

export type BadgeTone = 'info' | 'success' | 'warning' | 'danger'

/** The pill a student sees on an assignment. A late hand-in is "late", not "overdue": overdue means still to do. */
export function assignmentBadge(a: Pick<StudentAssignment, 'state' | 'late' | 'overdue'>): { text: string; tone: BadgeTone } {
  if (a.state === 'SUBMITTED' || a.state === 'GRADED') {
    return a.late ? { text: 'Predată cu întârziere', tone: 'warning' } : { text: 'Predată', tone: 'success' }
  }
  if (a.overdue) return { text: 'Termen depășit', tone: 'danger' }
  return a.state === 'IN_PROGRESS' ? { text: 'În lucru', tone: 'info' } : { text: 'De făcut', tone: 'info' }
}

/** "1 oră", "5 ore", "22 de ore": Romanian takes "de" from 20 up (21, 22... but not 105). */
function plural(n: number, one: string, many: string): string {
  if (n === 1) return `1 ${one}`
  const rest = n % 100
  return rest === 0 || rest >= 20 ? `${n} de ${many}` : `${n} ${many}`
}

const HOUR = 3_600_000
const DAY = 24 * HOUR

/** How far off the deadline is, in words, to the nearest unit: "peste 3 zile", "peste 5 ore", "depășit cu 2 zile". */
export function dueCountdown(dueIso: string, nowMs: number): string {
  const diff = Date.parse(dueIso) - nowMs
  if (diff >= 0) {
    if (diff < HOUR) return 'în mai puțin de o oră'
    if (diff < DAY) return `peste ${plural(Math.round(diff / HOUR), 'oră', 'ore')}`
    return `peste ${plural(Math.round(diff / DAY), 'zi', 'zile')}`
  }
  const late = -diff
  if (late < HOUR) return 'depășit'
  if (late < DAY) return `depășit cu ${plural(Math.round(late / HOUR), 'oră', 'ore')}`
  return `depășit cu ${plural(Math.round(late / DAY), 'zi', 'zile')}`
}

/** `<input type="datetime-local">` value (local time, no zone) -> the ISO instant the API takes. */
export function localInputToIso(value: string): string {
  return new Date(value).toISOString()
}

/** The reverse: an ISO instant -> the local `YYYY-MM-DDTHH:mm` a datetime-local input shows. */
export function isoToLocalInput(iso: string): string {
  const d = new Date(iso)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}
