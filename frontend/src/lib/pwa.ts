import { useSyncExternalStore } from 'react'

/**
 * E11 PWA: the service worker and the "install the app" prompt.
 *
 * Chrome/Edge/Android fire `beforeinstallprompt` once, early - often before React has drawn anything - so it is caught
 * here, at start-up, and kept until a button asks for it. Safari (iPhone/iPad) has no such event: installing there is
 * "Partajează → Adaugă pe ecranul principal", which the page can only explain.
 */

/** The non-standard event Chrome fires when the app may be installed. */
interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

let deferred: BeforeInstallPromptEvent | null = null
let installed = false
const listeners = new Set<() => void>()
const notify = () => listeners.forEach((listener) => listener())

/** Starts listening for the install prompt. Call once, before the app renders. */
export function captureInstallPrompt(target: Window = window): void {
  target.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault() // no mini-infobar: the app offers it where it makes sense
    deferred = event as BeforeInstallPromptEvent
    notify()
  })
  target.addEventListener('appinstalled', () => {
    deferred = null
    installed = true
    notify()
  })
}

/** Registers /sw.js in a production build (never in `vite dev`, where it would fight hot reload). */
export function registerServiceWorker(nav: Navigator = navigator, production = import.meta.env.PROD): void {
  if (!production || !('serviceWorker' in nav)) return
  window.addEventListener('load', () => {
    nav.serviceWorker.register('/sw.js', { scope: '/' }).catch(() => undefined) // the app works without it
  })
}

/** Whether the page runs as the installed app (home-screen icon), not in a browser tab. */
export function isStandalone(win: Window = window): boolean {
  return win.matchMedia?.('(display-mode: standalone)').matches || (win.navigator as { standalone?: boolean }).standalone === true
}

/** iPhone / iPad (iPadOS reports itself as a Mac with a touch screen): installing is done from the Share menu. */
export function isAppleMobile(nav: Navigator = navigator): boolean {
  return /iPhone|iPad|iPod/.test(nav.userAgent) || (/Macintosh/.test(nav.userAgent) && nav.maxTouchPoints > 1)
}

export type InstallState = 'installed' | 'available' | 'apple' | 'unsupported'

function currentState(): InstallState {
  if (installed || isStandalone()) return 'installed'
  if (deferred) return 'available'
  if (isAppleMobile()) return 'apple'
  return 'unsupported'
}

/** What the "install" UI can do on this device, kept up to date as the browser's events arrive. */
export function useInstallState(): InstallState {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    currentState,
    () => 'unsupported',
  )
}

/** Shows the browser's own install dialog; true when the person installed it. The prompt can be used only once. */
export async function promptInstall(): Promise<boolean> {
  const event = deferred
  if (!event) return false
  deferred = null
  await event.prompt()
  const { outcome } = await event.userChoice
  if (outcome === 'accepted') installed = true
  notify()
  return outcome === 'accepted'
}

/** Test seam: forget everything captured so far. */
export function resetInstallStateForTests(): void {
  deferred = null
  installed = false
  notify()
}
