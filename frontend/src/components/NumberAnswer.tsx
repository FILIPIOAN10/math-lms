import type { ReactNode } from 'react'
import { Input } from '@/components/ui/input'
import { isNumber, MAX_NUMBER_LENGTH } from '@/lib/numericAnswer'

/** Where a student types a number. A plain text box: a phone's number pad has no comma, slash or brackets. */
export function NumberAnswer({ id, label, value, disabled, onChange, onCommit, children }: {
  id: string
  label: string
  value: string
  disabled?: boolean
  onChange: (value: string) => void
  /** The student left the box or pressed Enter. */
  onCommit?: () => void
  children?: ReactNode
}) {
  const hintId = `${id}-hint`
  return (
    <div className="space-y-1">
      <div className="flex flex-wrap items-center gap-2">
        <Input
          id={id}
          data-testid="item-number"
          value={value}
          disabled={disabled}
          maxLength={MAX_NUMBER_LENGTH}
          autoComplete="off"
          aria-label={label}
          aria-describedby={hintId}
          className="w-44 max-sm:w-full"
          onChange={(e) => onChange(e.target.value)}
          onBlur={onCommit}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault()
              onCommit?.()
            }
          }}
        />
        {children}
      </div>
      <p id={hintId} className="text-xs text-muted-foreground">Scrie doar numărul: 12, 0,5, 1/2 sau 0,(3).</p>
      {value.trim() !== '' && !isNumber(value) && (
        <p role="status" className="text-sm text-amber-700 dark:text-amber-300">
          Nu pare un număr. Așa nu primește puncte, chiar dacă rezultatul e corect.
        </p>
      )}
    </div>
  )
}
