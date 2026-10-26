import { useRef, useState, type KeyboardEvent } from 'react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

/** Up to this many points the choice is a row of buttons (one tap); above it, a number box. */
export const MAX_BUTTONS = 10

/**
 * Focus the points of the next answer (or requirement) on the page, after the one holding `from`. Run after React has
 * shown the points just picked: leaving a group sends its value, and it must be the new one.
 */
function focusNextPicker(from: Element) {
  setTimeout(() => {
    const all = [...document.querySelectorAll<HTMLElement>('[data-points-picker]')]
    const next = all[all.findIndex((el) => el.contains(from)) + 1]
    const target = next?.matches('input') ? next : next?.querySelector<HTMLElement>('[tabindex="0"]')
    target?.focus()
  }, 0)
}

/**
 * The points for one answer. For a question worth up to 10 p: a radio group of buttons 0…max (arrow keys move and pick,
 * like any radio group; saved when focus leaves the group), each tap is final; a digit key picks those points, saves
 * them and moves on to the next points on the page, so a stack of answers is graded without the mouse. Above 10 p: a
 * number box, final on blur.
 * `onChange(points, commit)`: `commit` says the teacher is done (a tap, or leaving the box) - time to save.
 */
export function PointsPicker({
  max,
  value,
  onChange,
  disabled = false,
  autoFocus = false,
  label = 'Puncte acordate',
  testId = 'grade-points',
}: {
  max: number
  value: number | null
  onChange: (points: number | null, commit: boolean) => void
  disabled?: boolean
  autoFocus?: boolean
  /** What a screen reader calls the group (one per requirement on an EN problem). */
  label?: string
  /** The group's data-testid; each button is `${testId}-${n}`. */
  testId?: string
}) {
  const buttons = useRef<(HTMLButtonElement | null)[]>([])
  const [text, setText] = useState(value === null ? '' : String(value))

  if (max > MAX_BUTTONS) {
    const parse = (raw: string): number | null => {
      const n = Number(raw)
      return raw.trim() === '' || !Number.isInteger(n) || n < 0 || n > max ? null : n
    }
    return (
      <span className="flex items-center gap-2">
        <Input
          type="number"
          data-testid={testId}
          data-points-picker=""
          min={0}
          max={max}
          value={text}
          onChange={(e) => {
            setText(e.target.value)
            onChange(parse(e.target.value), false)
          }}
          onBlur={() => onChange(parse(text), true)}
          disabled={disabled}
          className="w-24"
          aria-label={label}
          aria-invalid={text.trim() !== '' && parse(text) === null}
          autoFocus={autoFocus}
        />
        <span className="text-sm text-muted-foreground">/ {max} p</span>
        {text.trim() !== '' && parse(text) === null && (
          <span className="text-sm text-destructive">Punctajul e un număr întreg de la 0 la {max}.</span>
        )}
      </span>
    )
  }

  const choices = Array.from({ length: max + 1 }, (_, n) => n)
  // Roving focus: the picked button (or 0) is the one Tab reaches; arrows move within the group.
  const focusable = value ?? 0

  function onKeyDown(e: KeyboardEvent<HTMLButtonElement>, n: number) {
    if (/^[0-9]$/.test(e.key) && !e.ctrlKey && !e.metaKey && !e.altKey) {
      const digit = Number(e.key)
      if (digit > max) return
      e.preventDefault()
      buttons.current[digit]?.focus()
      onChange(digit, true)
      focusNextPicker(e.currentTarget)
      return
    }
    const step = e.key === 'ArrowRight' || e.key === 'ArrowDown' ? 1 : e.key === 'ArrowLeft' || e.key === 'ArrowUp' ? -1 : 0
    const to = e.key === 'Home' ? 0 : e.key === 'End' ? max : step === 0 ? null : (n + step + max + 1) % (max + 1)
    if (to === null) return
    e.preventDefault()
    buttons.current[to]?.focus()
    onChange(to, false) // arrowing past 3 on the way to 5 is not three grades: saved when focus leaves the group
  }

  return (
    <span className="flex flex-wrap items-center gap-2">
      <span
        role="radiogroup"
        aria-label={label}
        data-testid={testId}
        data-points-picker=""
        className="flex flex-wrap gap-1"
        onBlur={(e) => {
          if (!e.currentTarget.contains(e.relatedTarget as Node | null)) onChange(value, true)
        }}
      >
        {choices.map((n) => (
          <button
            key={n}
            ref={(el) => { buttons.current[n] = el }}
            type="button"
            role="radio"
            aria-checked={value === n}
            tabIndex={n === focusable ? 0 : -1}
            data-testid={`${testId}-${n}`}
            disabled={disabled}
            autoFocus={autoFocus && n === focusable}
            onClick={() => onChange(n, true)}
            onKeyDown={(e) => onKeyDown(e, n)}
            className={cn(
              'h-11 min-w-11 rounded-lg border sm:h-9 sm:min-w-9 px-2 text-sm font-medium tabular-nums outline-none transition-colors',
              'focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50',
              value === n ? 'border-primary bg-primary text-primary-foreground' : 'border-border bg-background hover:bg-muted',
            )}
          >
            {n}
          </button>
        ))}
      </span>
      <span className="text-sm text-muted-foreground">/ {max} p</span>
    </span>
  )
}
