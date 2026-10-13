import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { MathContent } from '@/components/MathContent'
import { type EnArea, type EnChapterInput, type EnExampleInput } from '@/lib/api'
import { EN_AREAS } from '@/lib/enChapters'
import { errorMessage } from '@/lib/errors'

const selectClass =
  'h-9 rounded-lg border border-border bg-background px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50'

/** Create or edit an EN chapter: its code (how questions point at it), title, part and the optional text on its page. */
export function EnChapterDialog({
  open,
  onClose,
  initial,
  onSubmit,
}: {
  open: boolean
  onClose: () => void
  initial: EnChapterInput | null
  onSubmit: (input: EnChapterInput) => Promise<void>
}) {
  const [code, setCode] = useState(initial?.code ?? '')
  const [title, setTitle] = useState(initial?.title ?? '')
  const [area, setArea] = useState<EnArea>(initial?.area ?? 'ALGEBRA')
  const [notes, setNotes] = useState(initial?.notes ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await onSubmit({ code: code.trim(), title: title.trim(), area, notes: notes.trim() || null })
      onClose()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()} title={initial ? 'Editează capitolul' : 'Capitol nou'} wide>
      <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
        <div className="grid gap-4 sm:grid-cols-[8rem_1fr_10rem]">
          <div className="flex flex-col gap-2">
            <Label htmlFor="en-code">Cod</Label>
            <Input id="en-code" value={code} onChange={(e) => setCode(e.target.value)} placeholder="A12" maxLength={10} required autoFocus />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="en-title">Titlu</Label>
            <Input id="en-title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} required />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="en-area">Partea</Label>
            <select id="en-area" className={selectClass} value={area} onChange={(e) => setArea(e.target.value as EnArea)}>
              {EN_AREAS.map((a) => (
                <option key={a.value} value={a.value}>{a.label}</option>
              ))}
            </select>
          </div>
        </div>
        <p className="-mt-2 text-xs text-muted-foreground">
          Codul leagă întrebările din teste de această pagină: doar litere și cifre, de exemplu A1 sau G9.
        </p>
        <div className="flex flex-col gap-2">
          <Label htmlFor="en-notes">Text pe pagină (opțional)</Label>
          <Textarea
            id="en-notes"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Ce trebuie să știe elevul, pe scurt. Formulele între $...$, de exemplu $a^2 - b^2 = (a-b)(a+b)$."
            rows={4}
          />
          {notes.trim() && <MathContent className="rounded-lg bg-muted px-3 py-2 text-sm whitespace-pre-wrap">{notes}</MathContent>}
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>
            Anulează
          </Button>
          <Button type="submit" disabled={busy || code.trim() === '' || title.trim() === ''}>
            {busy ? 'Se salvează…' : 'Salvează'}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

/** Write or edit a worked example: the statement and its explained solution, each with its rendered preview. */
export function EnExampleDialog({
  open,
  onClose,
  initial,
  onSubmit,
}: {
  open: boolean
  onClose: () => void
  initial: EnExampleInput | null
  onSubmit: (input: EnExampleInput) => Promise<void>
}) {
  const [statement, setStatement] = useState(initial?.statement ?? '')
  const [solution, setSolution] = useState(initial?.solution ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await onSubmit({ statement: statement.trim(), solution: solution.trim() })
      onClose()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()} title={initial ? 'Editează modelul' : 'Model rezolvat nou'} wide>
      <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
        <div className="flex flex-col gap-2">
          <Label htmlFor="en-statement">Enunț</Label>
          <Textarea
            id="en-statement"
            value={statement}
            onChange={(e) => setStatement(e.target.value)}
            placeholder="Rezultatul calculului $30 - 18 : 3$ este:"
            rows={3}
            required
            autoFocus
          />
          {statement.trim() && <MathContent className="rounded-lg bg-muted px-3 py-2 text-sm whitespace-pre-wrap">{statement}</MathContent>}
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="en-solution">Rezolvare explicată</Label>
          <Textarea
            id="en-solution"
            value={solution}
            onChange={(e) => setSolution(e.target.value)}
            placeholder="Pas cu pas: întâi împărțirea, $18 : 3 = 6$, apoi scăderea, $30 - 6 = 24$."
            rows={6}
            required
          />
          {solution.trim() && <MathContent className="rounded-lg bg-muted px-3 py-2 text-sm whitespace-pre-wrap">{solution}</MathContent>}
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>
            Anulează
          </Button>
          <Button type="submit" disabled={busy || statement.trim() === '' || solution.trim() === ''}>
            {busy ? 'Se salvează…' : 'Salvează'}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
