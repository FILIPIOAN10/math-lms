import { useCallback, useLayoutEffect, useRef, useState } from 'react'

/**
 * idle: nothing changed since the page loaded · dirty: changed, not sent yet · incomplete: changed but not savable yet
 * (e.g. a comment without points) · saving · saved · error: the last save failed (flush() retries it).
 */
export type SaveStatus = 'idle' | 'dirty' | 'incomplete' | 'saving' | 'saved' | 'error'

export interface Autosave<T> {
  value: T
  /** Changes the value; nothing is sent until flush(). */
  setValue: (next: T) => void
  status: SaveStatus
  error: unknown
  /**
   * Sends the newest value if it differs from the last saved one. Never runs two saves at once: a change made while a
   * save is running is sent right after it. Resolves true once the newest value is saved, false if it cannot be
   * (not ready, or the save failed).
   */
  flush: () => Promise<boolean>
}

/**
 * Save-on-demand for a form whose endpoint is idempotent (sending the same body twice leaves the same state): the
 * caller decides when (on blur, on a click), the hook makes sure the newest value wins and nothing is sent twice.
 */
export function useAutosave<T>(
  initial: T,
  save: (value: T) => Promise<void>,
  { equals, isReady }: { equals: (a: T, b: T) => boolean; isReady: (value: T) => boolean },
): Autosave<T> {
  const [value, setState] = useState(initial)
  const [status, setStatus] = useState<SaveStatus>('idle')
  const [error, setError] = useState<unknown>(null)
  // What the save loop works with; React state above is only what the screen shows.
  const latest = useRef(initial)
  const lastSaved = useRef(initial)
  const savedOnce = useRef(false)
  const failure = useRef<unknown>(null)
  const inFlight = useRef<Promise<void> | null>(null)
  // The newest callbacks, without re-creating flush on every render.
  const callbacks = useRef({ save, equals, isReady })
  useLayoutEffect(() => {
    callbacks.current = { save, equals, isReady }
  })

  const describe = useCallback((): SaveStatus => {
    const { equals: same, isReady: ready } = callbacks.current
    if (inFlight.current) return 'saving'
    if (same(latest.current, lastSaved.current)) return savedOnce.current ? 'saved' : 'idle'
    if (failure.current) return 'error'
    return ready(latest.current) ? 'dirty' : 'incomplete'
  }, [])

  const show = useCallback(() => {
    setStatus(describe())
    setError(failure.current)
  }, [describe])

  const setValue = useCallback((next: T) => {
    latest.current = next
    setState(next)
    show()
  }, [show])

  const flush = useCallback(async (): Promise<boolean> => {
    for (;;) {
      if (inFlight.current) {
        await inFlight.current
        continue
      }
      const v = latest.current
      const { save: send, equals: same, isReady: ready } = callbacks.current
      if (same(v, lastSaved.current)) return true
      if (!ready(v)) {
        show()
        return false
      }
      const attempt = send(v)
      // The failure itself is handled below; this copy only tells others when the save is over.
      inFlight.current = attempt.catch(() => undefined).finally(() => {
        inFlight.current = null
        show()
      })
      show()
      try {
        await attempt
        lastSaved.current = v
        savedOnce.current = true
        failure.current = null
      } catch (e) {
        failure.current = e
        return false
      }
      // loop: if the value changed while it was being saved, send the newer one
    }
  }, [show])

  return { value, setValue, status, error, flush }
}
