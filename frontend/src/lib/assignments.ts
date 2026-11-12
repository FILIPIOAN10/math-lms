import { type StudentAssignment } from '@/lib/api'

export type BadgeTone = 'info' | 'success' | 'warning' | 'danger'

/** The colours of a status pill. */
export const BADGE_TONES: Record<BadgeTone, string> = {
  info: 'bg-sky-500/15 text-sky-700 dark:text-sky-300',
  success: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300',
  warning: 'bg-amber-500/15 text-amber-700 dark:text-amber-300',
  danger: 'bg-destructive/15 text-destructive',
}

/** Whether the homework was handed in, whatever happened after: a retake does not undo it. */
export function isHandedIn(a: Pick<StudentAssignment, 'state'>): boolean {
  return a.state === 'SUBMITTED' || a.state === 'GRADED'
}

/** The pill a student sees on an assignment. A late hand-in is "late", not "overdue": overdue means still to do. */
export function assignmentBadge(
  a: Pick<StudentAssignment, 'state' | 'late' | 'overdue'> & { excused?: boolean; availableFrom?: string | null },
): { text: string; tone: BadgeTone } {
  // Only the teacher's page for one student lists a homework not released yet.
  if (a.availableFrom) return { text: 'Programată', tone: 'warning' }
  if (a.excused) return { text: 'Scutită', tone: 'info' }
  if (isHandedIn(a)) {
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
  // Whole units, rounded down: "peste 2 ore" with 1 h 31 min left would promise time the student does not have.
  if (diff >= 0) {
    if (diff < HOUR) return 'în mai puțin de o oră'
    if (diff < DAY) return `peste ${plural(Math.floor(diff / HOUR), 'oră', 'ore')}`
    return `peste ${plural(Math.floor(diff / DAY), 'zi', 'zile')}`
  }
  const late = -diff
  if (late < HOUR) return 'depășit'
  if (late < DAY) return `depășit cu ${plural(Math.floor(late / HOUR), 'oră', 'ore')}`
  return `depășit cu ${plural(Math.floor(late / DAY), 'zi', 'zile')}`
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

/** A week from now at 20:00: the usual "for next lesson" deadline, easy to change. As a datetime-local value. */
export function defaultHomeworkDue(nowMs: number): string {
  const d = new Date(nowMs + 7 * 86_400_000)
  d.setHours(20, 0, 0, 0)
  return isoToLocalInput(d.toISOString())
}

/** Tomorrow at 08:00: the usual "before the lesson" moment. As a datetime-local value. */
export function defaultRelease(nowMs: number): string {
  const d = new Date(nowMs + 86_400_000)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T08:00`
}

/**
 * The scheduled release as an ISO instant (null = not scheduled), or the error to show: it must be in the future and
 * before the deadline. Checked here so a typo is caught before the round trip; the server checks again.
 */
export function releaseOf(release: string, dueIso: string, nowMs: number): { iso: string | null } | { error: string } {
  if (release === '') return { iso: null }
  const iso = localInputToIso(release)
  if (Date.parse(iso) <= nowMs) return { error: 'Data la care apare tema trebuie să fie în viitor.' }
  if (Date.parse(iso) >= Date.parse(dueIso)) return { error: 'Tema trebuie să apară înainte de termen.' }
  return { iso }
}
