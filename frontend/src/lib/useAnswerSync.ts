import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { ApiError, type AnswerFeedback } from '@/lib/api'
import { AnswerQueue, type PendingAnswer } from '@/lib/answerQueue'

/** saving: on its way · queued: waiting for the connection (it retries by itself). Nothing = on the server. */
export type SyncStatus = 'saving' | 'queued'

const MAX_DELAY_MS = 30_000

/**
 * Worth sending again later: no connection (fetch throws a TypeError), the server or the proxy is down or busy, or the
 * rate limiter asks to slow down. Anything else is a real answer from the server and retrying would not change it.
 */
export function isRetryable(e: unknown): boolean {
  if (e instanceof TypeError) return true
  return e instanceof ApiError && (e.status === 0 || e.status === 429 || e.status >= 500)
}

/**
 * Sends a quiz's answers through an {@link AnswerQueue}: each choice is stored first, then sent; one request per
 * question at a time; failures that may pass are retried after 1 s, 2 s, 4 s… (at most 30 s, with jitter, so a
 * classroom whose Wi-Fi comes back does not reconnect in the same second), and at once when the browser says it is
 * online again. Refusals go to `onRejected`. The answer PUT is idempotent, so sending one twice is harmless.
 */
export function useAnswerSync({
  attemptId,
  send,
  onSaved,
  onRejected,
}: {
  attemptId: number | null
  send: (answer: PendingAnswer, clientId: string) => Promise<AnswerFeedback | null>
  /** The server has `answer` (and, in a practice, its verdict); not called when a newer choice is still waiting. */
  onSaved: (answer: PendingAnswer, feedback: AnswerFeedback | null) => void
  /** The server refused `answer` for good (not a connection problem): it is dropped from the queue. */
  onRejected: (answer: PendingAnswer, error: unknown) => void
}) {
  const queue = useRef<AnswerQueue | null>(null)
  const inFlight = useRef(new Set<number>())
  const failures = useRef(0)
  const timer = useRef<number | null>(null)
  // flush() callers: told true when the queue is empty, false when a whole round of sending failed (no connection).
  const waiters = useRef(new Set<(empty: boolean) => void>())
  // What each question's answer is doing, rebuilt after every change to the queue.
  const [statuses, setStatuses] = useState<Map<number, SyncStatus>>(() => new Map())
  const [online, setOnline] = useState(() => (typeof navigator === 'undefined' ? true : navigator.onLine))
  const callbacks = useRef({ send, onSaved, onRejected })
  useLayoutEffect(() => {
    callbacks.current = { send, onSaved, onRejected }
  })

  const changed = useCallback(() => {
    const next = new Map<number, SyncStatus>()
    for (const entry of queue.current?.pending() ?? []) {
      next.set(entry.itemId, inFlight.current.has(entry.itemId) ? 'saving' : 'queued')
    }
    setStatuses(next)
    if (queue.current && queue.current.pending().length === 0) {
      waiters.current.forEach((wake) => wake(true))
      waiters.current.clear()
    }
  }, [])

  // pump() calls itself again from its own callbacks: through a ref, so the callback never reads itself mid-definition.
  const again = useRef<() => void>(() => undefined)
  const pump = useCallback(() => {
    const q = queue.current
    if (!q) return
    if (timer.current !== null) {
      window.clearTimeout(timer.current)
      timer.current = null
    }
    for (const entry of q.pending()) {
      if (inFlight.current.has(entry.itemId)) continue
      inFlight.current.add(entry.itemId)
      callbacks.current.send(entry, q.clientId).then(
        (feedback) => {
          failures.current = 0
          inFlight.current.delete(entry.itemId)
          q.settle(entry)
          // A newer choice made meanwhile is still waiting: its feedback is the one that counts.
          if (!q.pendingFor(entry.itemId)) callbacks.current.onSaved(entry, feedback)
          changed()
          again.current()
        },
        (error: unknown) => {
          inFlight.current.delete(entry.itemId)
          if (isRetryable(error)) {
            failures.current++
            if (inFlight.current.size === 0) {
              // Nothing got through this round: a waiting hand-in hears it now instead of after its whole timeout.
              waiters.current.forEach((wake) => wake(false))
              waiters.current.clear()
            }
            if (timer.current === null) {
              const delay = Math.min(MAX_DELAY_MS, 1000 * 2 ** (failures.current - 1)) * (0.5 + Math.random() / 2)
              timer.current = window.setTimeout(() => {
                timer.current = null
                again.current()
              }, delay)
            }
          } else {
            q.settle(entry)
            callbacks.current.onRejected(entry, error)
          }
          changed()
        },
      )
    }
    changed()
  }, [changed])

  useLayoutEffect(() => {
    again.current = pump
  }, [pump])

  // The attempt's queue, with whatever a reload or a closed tab left in it: send it now.
  useEffect(() => {
    if (attemptId === null) return
    queue.current = new AnswerQueue(attemptId)
    pump()
    return () => {
      if (timer.current !== null) window.clearTimeout(timer.current)
      timer.current = null
    }
  }, [attemptId, pump])

  useEffect(() => {
    const up = () => {
      setOnline(true)
      failures.current = 0
      pump()
    }
    const down = () => setOnline(false)
    window.addEventListener('online', up)
    window.addEventListener('offline', down)
    return () => {
      window.removeEventListener('online', up)
      window.removeEventListener('offline', down)
    }
  }, [pump])

  const enqueue = useCallback((itemId: number, optionId: number) => {
    queue.current?.enqueue(itemId, optionId)
    pump()
  }, [pump])

  /**
   * Sends everything now and waits for the queue to empty: true when it did, false when a round of sending failed (no
   * connection) or `timeoutMs` passed first.
   */
  const flush = useCallback((timeoutMs: number) => {
    const q = queue.current
    if (!q || q.pending().length === 0) return Promise.resolve(true)
    failures.current = 0
    pump()
    return new Promise<boolean>((resolve) => {
      const wake = (empty: boolean) => {
        window.clearTimeout(limit)
        resolve(empty)
      }
      const limit = window.setTimeout(() => {
        waiters.current.delete(wake)
        resolve(q.pending().length === 0)
      }, timeoutMs)
      waiters.current.add(wake)
    })
  }, [pump])

  /** After hand-in: nothing of this attempt is kept or retried. */
  const clear = useCallback(() => {
    queue.current?.clear()
    if (timer.current !== null) window.clearTimeout(timer.current)
    timer.current = null
    changed()
  }, [changed])

  return {
    enqueue,
    flush,
    clear,
    online,
    statusOf: (itemId: number): SyncStatus | undefined => statuses.get(itemId),
    pendingCount: statuses.size,
    /** Some answers wait for a retry (not just on their way). */
    waiting: [...statuses.values()].some((s) => s === 'queued'),
  }
}
