import type { ReactNode } from 'react'
import { Input } from '@/components/ui/input'
import { isNumber, MAX_NUMBER_LENGTH } from '@/lib/numericAnswer'

/** Where a student types a number. A plain text box: a phone's number pad has no comma, slash or brackets. */
export function NumberAnswer({ id, label, value, disabled, onChange, onCommit, children, hint, warning, testId = 'item-number' }: {
  id: string
  label: string
  value: string
  disabled?: boolean
  /** The line under the box; the default is for a numeric question. */
  hint?: string
  /** What to say when the text is not a number; the default warns that it earns no points. */
  warning?: string
  testId?: string
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
          data-testid={testId}
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
      <p id={hintId} className="text-xs text-muted-foreground">{hint ?? 'Scrie doar numărul: 12, 0,5, 1/2 sau 0,(3).'}</p>
      {value.trim() !== '' && !isNumber(value) && (
        <p role="status" className="text-sm text-amber-700 dark:text-amber-300">
          {warning ?? 'Nu pare un număr. Așa nu primește puncte, chiar dacă rezultatul e corect.'}
        </p>
      )}
    </div>
  )
}
