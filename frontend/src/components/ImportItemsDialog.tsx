import { useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { MathContent } from '@/components/MathContent'
import type { ItemInput } from '@/lib/api'
import { roCount } from '@/lib/format'
import { parseItems } from '@/lib/importItems'

const EXAMPLE = `1. Soluția ecuației $2x + 5 = 13$ este: (2p)
a) $x = 3$
*b) $x = 4$
c) $x = 9$
R: $2x = 8 \\Rightarrow x = 4$
I: Mută termenul liber în dreapta.

2. Rezolvă ecuația $x^2 - 7x + 12 = 0$. (5p)
R: $\\Delta = 1$ (2p); $x_1 = 4$, $x_2 = 3$ (3p)`

/**
 * Paste a whole test as text, check the preview, add every item in one go. {@code onImport} returns null when all
 * items were added, otherwise the message to show (items added before the failure stay added).
 */
export function ImportItemsDialog({
  open,
  onClose,
  onImport,
}: {
  open: boolean
  onClose: () => void
  onImport: (items: ItemInput[]) => Promise<string | null>
}) {
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const parsed = useMemo(() => parseItems(text), [text])

  const choices = parsed.items.filter((i) => i.type === 'SINGLE_CHOICE').length
  const total = parsed.items.reduce((sum, i) => sum + i.points, 0)
  const ready = parsed.items.length > 0 && parsed.errors.length === 0

  async function run() {
    setBusy(true)
    setError(null)
    const failure = await onImport(parsed.items)
    setBusy(false)
    if (failure) {
      setError(failure)
    } else {
      onClose()
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => !o && onClose()}
      title="Importă subiecte din text"
      description="Un subiect începe cu „1.”, variantele cu a), b)… (* marchează varianta corectă), rezolvarea cu R:, indiciile cu I:. „(2p)” la finalul enunțului dă punctajul (implicit 1)."
    >
      <div className="flex max-h-[75vh] flex-col gap-4 overflow-y-auto pr-1">
        <div className="flex flex-col gap-2">
          <Label htmlFor="import-text">Textul testului</Label>
          <Textarea id="import-text" rows={12} value={text} placeholder={EXAMPLE}
                    onChange={(e) => setText(e.target.value)} className="font-mono text-xs" />
        </div>

        {parsed.errors.length > 0 && (
          <ul role="alert" className="list-disc space-y-1 pl-5 text-sm text-destructive">
            {parsed.errors.map((e) => <li key={e}>{e}</li>)}
          </ul>
        )}

        {parsed.items.length > 0 && (
          <div className="space-y-2" data-testid="import-preview">
            <p className="text-sm font-medium">
              {roCount(parsed.items.length, 'subiect', 'subiecte')}: {roCount(choices, 'grilă', 'grile')},{' '}
              {roCount(parsed.items.length - choices, 'deschis', 'deschise')} · Total: {total} p
            </p>
            <ol className="list-decimal space-y-1 pl-5 text-sm">
              {parsed.items.map((item, i) => (
                <li key={i}>
                  <MathContent className="inline">{item.statement}</MathContent>{' '}
                  <span className="text-muted-foreground">({item.points} p)</span>
                </li>
              ))}
            </ol>
          </div>
        )}

        {error && <p className="text-sm text-destructive">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Anulează</Button>
          <Button type="button" onClick={run} disabled={busy || !ready}>
            {busy ? 'Se adaugă…' : `Adaugă ${roCount(parsed.items.length, 'subiect', 'subiecte')}`}
          </Button>
        </div>
      </div>
    </Dialog>
  )
}
