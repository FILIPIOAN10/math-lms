/**
 * The grading page's own keys, on top of the points picker's digits and arrows: N / P go to the next / previous paper
 * in the list (nothing is finalised - the grades already given are saved as they are typed), C jumps to the paper's
 * comment, ? shows the list of keys. Never while typing, never with Ctrl/⌘/Alt (the browser's own shortcuts), never
 * while a dialog is open.
 */
export type GradingKey = 'next' | 'prev' | 'comment' | 'help'

const KEYS: Record<string, GradingKey> = { n: 'next', p: 'prev', c: 'comment', '?': 'help' }

/** Whether the key goes into something being typed in (a comment, a points field, a search box). */
export function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  if (target.isContentEditable) return true
  if (target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement) return true
  if (target instanceof HTMLInputElement) {
    return !['button', 'checkbox', 'radio', 'submit', 'reset', 'range'].includes(target.type)
  }
  return false
}

export function gradingKey(
  e: Pick<KeyboardEvent, 'key' | 'ctrlKey' | 'metaKey' | 'altKey' | 'target'>,
  dialogOpen: boolean,
): GradingKey | null {
  if (e.ctrlKey || e.metaKey || e.altKey || dialogOpen || isTypingTarget(e.target)) return null
  return KEYS[e.key.toLowerCase()] ?? null
}

/** The neighbour of `current` in the list shown, or null at either end (or when it is not in the list). */
export function neighbourPaper(ids: number[], current: number, step: 1 | -1): number | null {
  const at = ids.indexOf(current)
  if (at < 0) return null
  return ids[at + step] ?? null
}

/** What the "?" dialog lists. */
export const GRADING_KEYS: { keys: string[]; what: string }[] = [
  { keys: ['0–9'], what: 'dă punctajul întrebării alese și trece la următoarea' },
  { keys: ['←', '→'], what: 'schimbă punctajul' },
  { keys: ['N'], what: 'lucrarea următoare din listă (nu finalizează nimic)' },
  { keys: ['P'], what: 'lucrarea dinainte' },
  { keys: ['C'], what: 'comentariul pentru toată lucrarea' },
  { keys: ['?'], what: 'lista asta' },
]
