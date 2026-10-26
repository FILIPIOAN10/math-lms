/**
 * Light / dark / follow the system, chosen in "Contul meu" and kept in this browser only (a per-device preference, not
 * account data). The `.dark` class on <html> is what the CSS tokens in index.css switch on. public/theme-boot.js applies
 * the same choice before the app loads, so a dark page never flashes white; keep its key in step with THEME_KEY.
 */
export type ThemeChoice = 'light' | 'dark' | 'system'

export const THEME_KEY = 'mathlms-theme'

const CHOICES: ThemeChoice[] = ['light', 'dark', 'system']

/** The saved choice; light when there is none (the app has always been light) or storage is blocked. */
export function readThemeChoice(): ThemeChoice {
  try {
    const saved = localStorage.getItem(THEME_KEY)
    return CHOICES.includes(saved as ThemeChoice) ? (saved as ThemeChoice) : 'light'
  } catch {
    return 'light'
  }
}

export function saveThemeChoice(choice: ThemeChoice) {
  try {
    localStorage.setItem(THEME_KEY, choice)
  } catch {
    // Private mode or blocked storage: the choice still applies until the page is closed.
  }
}

function systemIsDark(): boolean {
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
}

/** Puts the page in the chosen theme, and the phone's status bar colour with it. */
export function applyTheme(choice: ThemeChoice) {
  const dark = choice === 'dark' || (choice === 'system' && systemIsDark())
  document.documentElement.classList.toggle('dark', dark)
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#0a0a0a' : '#171717')
}

/** With "system", follows the phone or computer when it switches between light and dark. Returns the unsubscribe. */
export function followSystemTheme(choice: ThemeChoice): () => void {
  if (choice !== 'system' || !window.matchMedia) return () => {}
  const query = window.matchMedia('(prefers-color-scheme: dark)')
  const onChange = () => applyTheme('system')
  query.addEventListener?.('change', onChange)
  return () => query.removeEventListener?.('change', onChange)
}

let stopFollowing = () => {}

/** At start-up: the saved choice (the boot script already set the class; this also follows a "system" switch). */
export function initTheme() {
  const choice = readThemeChoice()
  applyTheme(choice)
  stopFollowing()
  stopFollowing = followSystemTheme(choice)
}

/** The user picked a theme: remember it, show it now, and follow the system if that is the pick. */
export function chooseTheme(choice: ThemeChoice) {
  saveThemeChoice(choice)
  applyTheme(choice)
  stopFollowing()
  stopFollowing = followSystemTheme(choice)
}
