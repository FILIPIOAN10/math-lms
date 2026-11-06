import type { MyAttemptDto } from '@/lib/api'
import { roCount } from '@/lib/format'

/**
 * The quiz countdown. The deadline is decided by the server; the browser only displays it. To stay correct on a
 * device whose own clock is wrong, the remaining time is never computed as "deadline minus the browser's now":
 * it is the server's distance to the deadline when the page received it, minus how long the browser's clock has
 * advanced since (a duration, immune to the clock being set wrongly).
 */
export function secondsLeft(deadlineIso: string, serverNowIso: string, receivedAtMs: number, nowMs: number): number {
  const leftAtReceiptMs = Date.parse(deadlineIso) - Date.parse(serverNowIso)
  const leftMs = leftAtReceiptMs - (nowMs - receivedAtMs)
  return Math.max(0, Math.ceil(leftMs / 1000))
}

/**
 * Seconds left on a test the student started and walked away from - its clock keeps running on the server - counted
 * from the server's clock as in secondsLeft. Null when there is no clock: untimed, a practice, or already handed in.
 * Zero once the time is up and the server has not handed the paper in yet.
 */
export function runningClock(
  attempt: Pick<MyAttemptDto, 'mode' | 'status' | 'deadlineAt' | 'serverNow'>,
  receivedAtMs: number,
  nowMs: number,
): number | null {
  if (attempt.mode !== 'TEST' || attempt.status !== 'IN_PROGRESS' || !attempt.deadlineAt || !attempt.serverNow) return null
  // "now" may be older than the list (a ticking clock set before it arrived): count from the arrival then.
  return secondsLeft(attempt.deadlineAt, attempt.serverNow, receivedAtMs, Math.max(nowMs, receivedAtMs))
}

/**
 * How long a test still has, for a card or a banner: "1 h 35 min", "42 de minute", "mai puțin de un minut". Whole
 * minutes, rounded down: a card must never promise the student time they do not have.
 */
export function formatTimeLeft(totalSeconds: number): string {
  const minutes = Math.floor(Math.max(0, totalSeconds) / 60)
  if (minutes < 1) return 'mai puțin de un minut'
  if (minutes < 60) return roCount(minutes, 'minut', 'minute')
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`
}

/** "⏱ Mai ai 1 h 35 min" for a test the student left while its clock runs, or that its time is up. */
export function clockText(seconds: number): string {
  return seconds > 0 ? `⏱ Mai ai ${formatTimeLeft(seconds)}` : '⏱ Timpul a expirat: lucrarea se predă singură'
}

/** What the big card says under a running clock: it does not stop because the page is closed. */
export function clockNote(seconds: number): string {
  return seconds > 0
    ? 'Cronometrul merge și cu pagina închisă; când expiră, lucrarea se predă singură cu ce ai salvat.'
    : 'Lucrarea se predă singură cu ce ai salvat.'
}

/** `m:ss`, or `h:mm:ss` from one hour up. */
export function formatClock(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds))
  const hours = Math.floor(s / 3600)
  const minutes = Math.floor((s % 3600) / 60)
  const seconds = s % 60
  const mm = hours > 0 ? String(minutes).padStart(2, '0') : String(minutes)
  const ss = String(seconds).padStart(2, '0')
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`
}
