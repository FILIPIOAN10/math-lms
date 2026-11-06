import { useEffect, useState } from 'react'

/**
 * The time now, refreshed every `intervalMs`: enough to keep a "mai ai 35 de minute" on a card honest while the page
 * stays open, without re-rendering every second.
 */
export function useNow(intervalMs: number): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), intervalMs)
    return () => window.clearInterval(timer)
  }, [intervalMs])
  return now
}
