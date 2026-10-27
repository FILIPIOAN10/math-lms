import { useState, type KeyboardEvent } from 'react'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { MathContent } from '@/components/MathContent'
import { cn } from '@/lib/utils'

/**
 * A text field that shows its LaTeX typeset - as the student will see it - until it is clicked (or reached with Tab and
 * Enter): then it is a plain box to edit, and leaving it typesets it again. An empty field is a box from the start.
 * {@code multiline} makes it a textarea (a statement); the box keeps {@code id}, {@code placeholder} and key handling.
 */
export function MathField({
  id,
  value,
  onChange,
  label,
  multiline = false,
  placeholder,
  onKeyDown,
  className,
  livePreview = false,
}: {
  id?: string
  value: string
  onChange: (value: string) => void
  /** The accessible name of the typeset view ("Varianta a)"); the box itself is named by its own label or placeholder. */
  label?: string
  multiline?: boolean
  placeholder?: string
  onKeyDown?: (e: KeyboardEvent<HTMLInputElement>) => void
  className?: string
  /** While editing, the typeset text under the box (a statement: long LaTeX is easier to check as it is typed). */
  livePreview?: boolean
}) {
  const [editing, setEditing] = useState(false)

  if (!editing && value.trim() !== '') {
    return (
      <button
        type="button"
        id={id}
        aria-label={label}
        title="Clic ca să modifici"
        onClick={() => setEditing(true)}
        className={cn(
          'w-full min-w-0 cursor-text rounded-lg border border-border bg-background px-3 py-1.5 text-left text-sm shadow-sm',
          'outline-none hover:border-ring focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50',
          multiline ? 'min-h-16' : 'min-h-9 max-sm:min-h-11',
          className,
        )}
      >
        <MathContent inline={!multiline}>{value}</MathContent>
      </button>
    )
  }
  return multiline ? (
    <>
      <Textarea
        id={id}
        value={value}
        autoFocus={editing}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        onFocus={() => setEditing(true)}
        onBlur={() => setEditing(false)}
        className={className}
      />
      {livePreview && value.trim() !== '' && (
        <MathContent className="rounded-md bg-muted/40 px-2 py-1 text-sm">{value}</MathContent>
      )}
    </>
  ) : (
    <Input
      id={id}
      value={value}
      autoFocus={editing}
      placeholder={placeholder}
      onChange={(e) => onChange(e.target.value)}
      onFocus={() => setEditing(true)} // an empty box being typed into stays a box
      onBlur={() => setEditing(false)}
      onKeyDown={onKeyDown}
      className={className}
    />
  )
}
