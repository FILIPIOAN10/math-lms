import { useEffect, useState } from 'react'

const COLORS = ['bg-indigo-500', 'bg-emerald-500', 'bg-amber-400', 'bg-sky-400', 'bg-pink-400']
const PIECES = 28
const DURATION_MS = 2600

/** A short, quiet shower of paper: decoration only, it takes no clicks and is gone by itself. */
export function Confetti() {
  const [done, setDone] = useState(false)
  useEffect(() => {
    const timer = setTimeout(() => setDone(true), DURATION_MS)
    return () => clearTimeout(timer)
  }, [])
  if (done) return null

  return (
    <div aria-hidden="true" data-testid="confetti" className="pointer-events-none fixed inset-x-0 top-0 z-50 overflow-hidden">
      {Array.from({ length: PIECES }, (_, i) => (
        <span
          key={i}
          className={`absolute top-0 h-2.5 w-1.5 rounded-sm ${COLORS[i % COLORS.length]}`}
          style={{
            left: `${((i * 37) % 100)}%`,
            animation: `confetti-fall ${1400 + (i * 53) % 900}ms ease-in ${(i * 29) % 400}ms both`,
          }}
        />
      ))}
    </div>
  )
}
