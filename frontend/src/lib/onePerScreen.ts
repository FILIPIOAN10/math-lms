/**
 * "O întrebare pe ecran": a test shown one question at a time, with Înainte / Înapoi and a swipe - easier on a phone
 * than one long page. A per-device preference, like the theme: kept in this browser only.
 */
export const ONE_PER_SCREEN_KEY = 'mathlms-one-per-screen'

export function readOnePerScreen(): boolean {
  try {
    return localStorage.getItem(ONE_PER_SCREEN_KEY) === '1'
  } catch {
    return false
  }
}

export function saveOnePerScreen(on: boolean): void {
  try {
    if (on) localStorage.setItem(ONE_PER_SCREEN_KEY, '1')
    else localStorage.removeItem(ONE_PER_SCREEN_KEY)
  } catch {
    // blocked storage: the choice lasts until the page closes
  }
}

/** How far a finger must travel sideways to count as a swipe, not a tap or a scroll that drifted. */
const MIN_SWIPE_PX = 70

/**
 * A finger moved (dx, dy): to the next question (swiped left), the previous one (swiped right), or neither - too short,
 * or more up-and-down than sideways (the student was scrolling).
 */
export function swipeDirection(dx: number, dy: number): 'next' | 'prev' | null {
  if (Math.abs(dx) < MIN_SWIPE_PX || Math.abs(dx) < 2 * Math.abs(dy)) return null
  return dx < 0 ? 'next' : 'prev'
}

/**
 * Whether a touch began on something that scrolls sideways by itself (a long formula, a wide figure) between `target`
 * and `container`: swiping there scrolls it, it must not change the question.
 */
export function startsInSideScroller(target: EventTarget | null, container: HTMLElement): boolean {
  for (let el = target instanceof HTMLElement ? target : null; el && el !== container; el = el.parentElement) {
    if (el.scrollWidth > el.clientWidth + 1) {
      const overflow = getComputedStyle(el).overflowX
      if (overflow === 'auto' || overflow === 'scroll') return true
    }
    if (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement) return true
  }
  return false
}
