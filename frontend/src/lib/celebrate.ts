const SEEN_KEY = 'mathlms:first-ten-celebrated'

/**
 * Whether to throw the confetti: on the first 10 this browser shows, once, and never for someone who asked for less
 * motion. Storage that cannot be read or written means no confetti rather than confetti on every visit.
 */
export function celebrateFirstTen(): boolean {
  if (window.matchMedia?.('(prefers-reduced-motion: no-preference)').matches !== true) return false
  try {
    if (localStorage.getItem(SEEN_KEY)) return false
    localStorage.setItem(SEEN_KEY, new Date().toISOString())
    return true
  } catch {
    return false
  }
}
