/**
 * Short notices at the bottom of the screen ("Salvat", "Mai ai 10 minute", "Ascuns · Anulează") that go away by
 * themselves: news the user does not have to answer, instead of a dialog in the way. One list for the whole app,
 * shown by <Toaster /> (mounted once in App); call `toast()` from anywhere. A tiny store of our own: four functions do
 * not need a dependency.
 */
export type ToastTone = 'default' | 'success' | 'warning' | 'danger'

export interface ToastAction {
  label: string
  onClick: () => void
}

export interface Toast {
  id: number
  message: string
  tone: ToastTone
  action?: ToastAction
  /** false: shown, not read out (a live region elsewhere already says it). */
  announce: boolean
}

export interface ToastOptions {
  message: string
  tone?: ToastTone
  /** A way back ("Anulează"): pressing it runs `onClick` and closes the toast. */
  action?: ToastAction
  /** A newer toast with the same key replaces this one (a clock warning, not two). */
  key?: string
  durationMs?: number
  announce?: boolean
}

/** Long enough to read a line; with a button, long enough to decide to press it. */
const PLAIN_MS = 5000
const WITH_ACTION_MS = 8000
/** More than this at once is a wall, not a notice: the oldest goes. */
const MAX_SHOWN = 3

let toasts: Toast[] = []
let nextId = 1
const keys = new Map<number, string>()
const timers = new Map<number, ReturnType<typeof setTimeout>>()
const listeners = new Set<() => void>()

function emit() {
  for (const listener of listeners) listener()
}

export function dismissToast(id: number) {
  const timer = timers.get(id)
  if (timer) clearTimeout(timer)
  timers.delete(id)
  keys.delete(id)
  const before = toasts.length
  toasts = toasts.filter((t) => t.id !== id)
  if (toasts.length !== before) emit()
}

export function toast({ message, tone = 'default', action, key, durationMs, announce = true }: ToastOptions): number {
  if (key) {
    for (const [id, k] of keys) if (k === key) dismissToast(id)
  }
  const id = nextId++
  if (key) keys.set(id, key)
  toasts = [...toasts, { id, message, tone, action, announce }]
  while (toasts.length > MAX_SHOWN) dismissToast(toasts[0].id)
  timers.set(id, setTimeout(() => dismissToast(id), durationMs ?? (action ? WITH_ACTION_MS : PLAIN_MS)))
  emit()
  return id
}

export function readToasts(): Toast[] {
  return toasts
}

export function subscribeToasts(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** Tests: start from an empty screen. */
export function clearToasts() {
  for (const t of [...toasts]) dismissToast(t.id)
}
