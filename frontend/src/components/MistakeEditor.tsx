import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { MathContent } from '@/components/MathContent'
import { saveOptionMistake, suggestOptionMistake, type OptionStat } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { MAX_MISTAKE } from '@/lib/quizStats'

/**
 * What a wrong option gives away, written where the teacher sees how many chose it. The AI only fills the box: nothing
 * reaches a student until the teacher presses "Salvează".
 */
export function MistakeEditor({ option, aiOn, onSaved }: { option: OptionStat; aiOn: boolean; onSaved: () => void }) {
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState(option.mistake ?? '')
  const [busy, setBusy] = useState(false)
  const [proposing, setProposing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function save() {
    setBusy(true)
    setError(null)
    try {
      await saveOptionMistake(option.id, text.trim())
      setEditing(false)
      onSaved()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  async function propose() {
    setProposing(true)
    setError(null)
    try {
      setText(await suggestOptionMistake(option.id))
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setProposing(false)
    }
  }

  if (!editing) {
    return option.mistake ? (
      <div className="flex w-full flex-wrap items-baseline gap-2 text-muted-foreground" data-testid="option-mistake">
        <MathContent inline>{option.mistake}</MathContent>
        <Button type="button" size="xs" variant="ghost" onClick={() => { setText(option.mistake ?? ''); setEditing(true) }}>
          Schimbă explicația
        </Button>
      </div>
    ) : (
      <Button type="button" size="xs" variant="outline" onClick={() => setEditing(true)}>
        Ce greșeală ascunde?
      </Button>
    )
  }

  return (
    <div className="w-full space-y-2" data-testid="option-mistake-editor">
      <Textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        maxLength={MAX_MISTAKE}
        aria-label={`Greșeala din spatele variantei ${option.letter})`}
        placeholder="Ai adunat numitorii. Aduci întâi la același numitor."
        className="min-h-16"
      />
      <div className="flex flex-wrap gap-2">
        <Button type="button" size="xs" disabled={busy} onClick={() => void save()}>
          {busy ? 'Se salvează...' : 'Salvează'}
        </Button>
        {aiOn && (
          <Button type="button" size="xs" variant="outline" disabled={proposing} onClick={() => void propose()}>
            {proposing ? 'AI-ul se gândește...' : 'Propune cu AI'}
          </Button>
        )}
        <Button type="button" size="xs" variant="ghost" onClick={() => { setEditing(false); setError(null) }}>
          Renunță
        </Button>
      </div>
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
    </div>
  )
}
