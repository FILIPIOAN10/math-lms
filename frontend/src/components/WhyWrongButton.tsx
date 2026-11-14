import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { askWhyWrong } from '@/lib/api'
import { errorMessage } from '@/lib/errors'

/**
 * „De ce am greșit?” under a wrong grilă whose option the teacher has not explained yet: the request goes to the
 * teacher, who writes (or drafts from the lessons) the explanation; it then appears here, for everyone who chose it.
 */
export function WhyWrongButton({ attemptId, itemId, requested }: { attemptId: number; itemId: number; requested: boolean }) {
  const [asked, setAsked] = useState(requested)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (asked) {
    return (
      <p data-testid="why-wrong-requested" className="text-sm text-muted-foreground">
        Ai întrebat „De ce am greșit?”. Profesorul scrie explicația, iar ea apare aici.
      </p>
    )
  }

  async function ask() {
    setBusy(true)
    setError(null)
    try {
      await askWhyWrong(attemptId, itemId)
      setAsked(true)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-1">
      <Button type="button" size="sm" variant="outline" disabled={busy} onClick={() => void ask()}>
        De ce am greșit?
      </Button>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  )
}
