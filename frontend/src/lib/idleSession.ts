/**
 * A session nobody uses for an hour closes itself (the server's `IDLE_TIMEOUT_MINUTES`). "Using" is the person at
 * the keyboard — a click, a key, a scroll, a touch — in any tab of the app: the last time is kept in localStorage so
 * every tab counts the same hour. The server hears about it through a ping at most once a minute
 * (`keepSessionAlive`); after the hour it refuses the session even if this tab was asleep and never signed out.
 */

/** Until the server names its own limit (the first ping answers with it). */
export const DEFAULT_IDLE_LIMIT_MS = 60 * 60_000
/** "Mai ești aici?" shows this long before the sign-out. */
export const WARNING_MS = 5 * 60_000
/** The server is told about activity at most this often. */
export const HEARTBEAT_MS = 60_000

const ACTIVITY_KEY = 'mathlms-last-activity'
const HEARTBEAT_KEY = 'mathlms-last-heartbeat'
// sessionStorage: the tab that signed out shows the reason on its own sign-in page.
const NOTICE_KEY = 'mathlms-sign-out-notice'

export type IdlePhase = { kind: 'active' } | { kind: 'warning'; leftMs: number } | { kind: 'expired' }

/** Where an idle stretch stands: still fine, in the last minutes before the sign-out, or over. */
export function idlePhase(now: number, lastActivity: number, limitMs: number): IdlePhase {
  const leftMs = lastActivity + limitMs - now
  if (leftMs <= 0) return { kind: 'expired' }
  if (leftMs <= WARNING_MS) return { kind: 'warning', leftMs }
  return { kind: 'active' }
}

function readTime(storage: Storage, key: string): number | null {
  try {
    const value = Number(storage.getItem(key))
    return storage.getItem(key) !== null && Number.isFinite(value) && value > 0 ? value : null
  } catch {
    return null
  }
}

function writeTime(storage: Storage, key: string, at: number) {
  try {
    storage.setItem(key, String(at))
  } catch {
    // Blocked storage: this tab still counts its own activity, it just cannot share it.
  }
}

export const readLastActivity = () => readTime(localStorage, ACTIVITY_KEY)
export const writeLastActivity = (at: number) => writeTime(localStorage, ACTIVITY_KEY, at)
export const readLastHeartbeat = () => readTime(localStorage, HEARTBEAT_KEY)
export const writeLastHeartbeat = (at: number) => writeTime(localStorage, HEARTBEAT_KEY, at)

/** Is this storage event another tab reporting activity? */
export const isActivityKey = (key: string | null) => key === ACTIVITY_KEY

/** On every sign-out: the next person's hour starts from their own sign-in. */
export function forgetActivity() {
  try {
    localStorage.removeItem(ACTIVITY_KEY)
    localStorage.removeItem(HEARTBEAT_KEY)
  } catch {
    // Nothing kept, nothing to forget.
  }
}

/** Why a session ended without the person asking: an idle hour, or the server had already closed it. */
export type SignOutNotice = 'idle' | 'ended'

export function noteSignOut(notice: SignOutNotice) {
  try {
    sessionStorage.setItem(NOTICE_KEY, notice)
  } catch {
    // The sign-in page just shows no reason.
  }
}

/** The reason, without forgetting it: React may render the sign-in page twice. */
export function readSignOutNotice(): SignOutNotice | null {
  try {
    const value = sessionStorage.getItem(NOTICE_KEY)
    return value === 'idle' || value === 'ended' ? value : null
  } catch {
    return null
  }
}

/** Once the sign-in page has shown it. */
export function clearSignOutNotice() {
  try {
    sessionStorage.removeItem(NOTICE_KEY)
  } catch {
    // Nothing to clear.
  }
}
