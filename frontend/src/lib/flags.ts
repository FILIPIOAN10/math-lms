/**
 * "Revin la ea" (Moodle's "Flag question"): the questions a student marked to come back to during one attempt. A note
 * to themselves, never graded, so it stays on this device: kept per attempt in localStorage (survives a reload or
 * "Salvează și ieși" on the same device) and dropped once the attempt is handed in. Storage that throws (private mode,
 * blocked site data) just means the flags last until the page closes.
 */
const key = (attemptId: number) => `mathlms-flags-${attemptId}`

export function loadFlags(attemptId: number): Set<number> {
  try {
    const raw = localStorage.getItem(key(attemptId))
    const ids: unknown = raw ? JSON.parse(raw) : []
    return new Set(Array.isArray(ids) ? ids.filter((id): id is number => typeof id === 'number') : [])
  } catch {
    return new Set()
  }
}

export function saveFlags(attemptId: number, flags: Set<number>): void {
  try {
    if (flags.size === 0) localStorage.removeItem(key(attemptId))
    else localStorage.setItem(key(attemptId), JSON.stringify([...flags]))
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
