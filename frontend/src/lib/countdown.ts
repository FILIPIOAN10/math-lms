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
