/**
 * "Revin la ea" (Moodle's "Flag question"): the questions a student marked to come back to during one attempt. A note
 * to themselves, never graded, so it stays on this device: kept per attempt in localStorage (survives a reload or
 * "Salvează și ieși" on the same device) and dropped once the attempt is handed in - here, or when its result is opened
 * (the time ran out, or it was handed in on another device). Marks left untouched for a month are swept whenever an
 * attempt is opened, so a forgotten attempt does not keep its key forever. Storage that throws (private mode, blocked
 * site data) just means the flags last until the page closes.
 */
const PREFIX = 'mathlms-flags-'
const key = (attemptId: number) => `${PREFIX}${attemptId}`
/** How long the marks of an attempt nobody opens again are kept. */
const KEEP_MS = 30 * 24 * 60 * 60 * 1000

/** What one key holds: the marked question ids and when they were last saved (absent in the first, array-only format). */
interface Stored {
  ids: number[]
  at: number | null
}

function parse(raw: string | null): Stored {
  const value: unknown = raw ? JSON.parse(raw) : []
  const numbers = (list: unknown) => (Array.isArray(list) ? list.filter((id): id is number => typeof id === 'number') : [])
  if (Array.isArray(value)) return { ids: numbers(value), at: null }
  if (value && typeof value === 'object') {
    const { ids, at } = value as { ids?: unknown; at?: unknown }
    return { ids: numbers(ids), at: typeof at === 'number' ? at : null }
  }
  return { ids: [], at: null }
}

/** Removes the marks of other attempts last saved more than a month ago. */
function sweep(keep: string, nowMs: number): void {
  for (let i = localStorage.length - 1; i >= 0; i--) {
    const name = localStorage.key(i)
    if (!name?.startsWith(PREFIX) || name === keep) continue
    try {
      const { at } = parse(localStorage.getItem(name))
      if (at !== null && nowMs - at > KEEP_MS) localStorage.removeItem(name)
    } catch {
      localStorage.removeItem(name) // unreadable: nothing worth keeping
    }
  }
}

export function loadFlags(attemptId: number): Set<number> {
  try {
    sweep(key(attemptId), Date.now())
    return new Set(parse(localStorage.getItem(key(attemptId))).ids)
  } catch {
    return new Set()
  }
}

export function saveFlags(attemptId: number, flags: Set<number>): void {
  try {
    if (flags.size === 0) localStorage.removeItem(key(attemptId))
    else localStorage.setItem(key(attemptId), JSON.stringify({ ids: [...flags], at: Date.now() }))
  } catch {
    // flags last until the page closes
  }
}

export function clearFlags(attemptId: number): void {
  try {
    localStorage.removeItem(key(attemptId))
  } catch {
    // nothing stored to clear
  }
}

/** The set with {@code itemId} switched on or off. */
export function toggled(flags: Set<number>, itemId: number): Set<number> {
  const next = new Set(flags)
  if (next.has(itemId)) next.delete(itemId)
  else next.add(itemId)
  return next
}
