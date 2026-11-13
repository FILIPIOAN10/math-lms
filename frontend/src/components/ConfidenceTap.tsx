import { Button } from '@/components/ui/button'
import type { Confidence } from '@/lib/api'
import { CONFIDENCE_CHOICES } from '@/lib/confidence'

/**
 * "Cât de bine o știi?" - one tap before "Verifică" in the daily review. Optional and never graded: a right answer
 * tapped "Am ghicit" just comes back after the same gap instead of a longer one. Tapping the chosen one again takes
 * it back.
 */
export function ConfidenceTap({
  value,
  onChange,
  disabled,
}: {
  value: Confidence | null
  onChange: (value: Confidence | null) => void
  disabled?: boolean
}) {
  return (
    <div data-testid="confidence-tap" role="group" aria-label="Cât de bine o știi?" className="flex flex-wrap items-center gap-2">
      <span className="text-sm text-muted-foreground">Cât de bine o știi?</span>
      {CONFIDENCE_CHOICES.map((choice) => (
        <Button
          key={choice.value}
          type="button"
          size="sm"
          variant={value === choice.value ? 'default' : 'outline'}
          aria-pressed={value === choice.value}
          disabled={disabled}
          onClick={() => onChange(value === choice.value ? null : choice.value)}
        >
          {choice.label}
        </Button>
      ))}
    </div>
  )
}
