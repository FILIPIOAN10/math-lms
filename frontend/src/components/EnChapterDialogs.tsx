import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { MarkdownText } from '@/components/MarkdownText'
import { MathContent } from '@/components/MathContent'
import { type EnArea, type EnChapterInput, type EnChapterSummary, type EnExampleInput } from '@/lib/api'
import { EN_AREAS, parentChoices } from '@/lib/enChapters'
import { errorMessage } from '@/lib/errors'

const selectClass =
  'h-9 rounded-lg border border-border bg-background px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50'

/**
 * Create or edit an EN chapter: its code (how questions point at it), title, part and the optional text on its page.
 * With `chapters` it also offers the chapter (or section) this page sits under and the page of the book it starts on;
 * without them, those are kept as they were.
 */
export function EnChapterDialog({
  open,
  onClose,
  initial,
  onSubmit,
  chapters,
  selfId = null,
}: {
  open: boolean
  onClose: () => void
  initial: EnChapterInput | null
  onSubmit: (input: EnChapterInput) => Promise<void>
  chapters?: EnChapterSummary[]
  selfId?: number | null
}) {
  const [code, setCode] = useState(initial?.code ?? '')
  const [title, setTitle] = useState(initial?.title ?? '')
  const [area, setArea] = useState<EnArea>(initial?.area ?? 'ALGEBRA')
  const [notes, setNotes] = useState(initial?.notes ?? '')
  const [parentId, setParentId] = useState<number | null>(initial?.parentId ?? null)
  const [bookPage, setBookPage] = useState(initial?.bookPage != null ? String(initial.bookPage) : '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const choices = chapters ? parentChoices(chapters, area, selfId) : null

  function chooseParent(value: string) {
    const id = value === '' ? null : Number(value)
    setParentId(id)
    const parent = id === null ? undefined : chapters?.find((c) => c.id === id)
    if (parent) setArea(parent.area) // a section is always in its chapter's part
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await onSubmit({
        code: code.trim(),
        title: title.trim(),
        area,
        notes: notes.trim() || null,
        parentId,
        bookPage: bookPage.trim() === '' ? null : Number(bookPage),
      })
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
            <select id="en-area" className={selectClass} value={area} disabled={parentId !== null}
              onChange={(e) => setArea(e.target.value as EnArea)}>
              {EN_AREAS.map((a) => (
                <option key={a.value} value={a.value}>{a.label}</option>
              ))}
            </select>
          </div>
        </div>
        <p className="-mt-2 text-xs text-muted-foreground">
          Codul leagă întrebările din teste de această pagină: doar litere și cifre, de exemplu A1 sau G9.
        </p>
        {choices && (
          <div className="grid gap-4 sm:grid-cols-[1fr_12rem]">
            <div className="flex flex-col gap-2">
              <Label htmlFor="en-parent">Sub capitolul</Label>
              <select id="en-parent" className={selectClass} value={parentId ?? ''} onChange={(e) => chooseParent(e.target.value)}>
                <option value="">— niciunul, e un capitol —</option>
                {choices.map(({ chapter, level }) => (
                  <option key={chapter.id} value={chapter.id}>
                    {'  '.repeat(level)}{chapter.code} · {chapter.title}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="en-book-page">Pagina din carte</Label>
              <Input
                id="en-book-page"
                type="number"
                min={1}
                max={5000}
                value={bookPage}
                onChange={(e) => setBookPage(e.target.value)}
                placeholder="ex. 12"
              />
            </div>
          </div>
        )}
        {choices && (
          <p className="-mt-2 text-xs text-muted-foreground">
            Un subcapitol nu are poze proprii: elevul îl deschide în capitolul lui, la pagina din carte unde începe.
          </p>
        )}
        <div className="flex flex-col gap-2">
          <Label htmlFor="en-notes">Text pe pagină (opțional)</Label>
          <Textarea
            id="en-notes"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Ce trebuie să știe elevul, pe scurt. Formulele între $...$, de exemplu $a^2 - b^2 = (a-b)(a+b)$."
            rows={16}
          />
          <p className="text-xs text-muted-foreground">
            Formulele între $…$ (de exemplu $a^2$), **îngroșat** între două stele, tabel cu linii care încep cu |.
            Mai jos vezi textul așa cum îl vede elevul.
          </p>
          {notes.trim() && (
            <div className="rounded-lg bg-muted px-3 py-2 text-sm" data-testid="en-notes-preview">
              <MarkdownText>{notes}</MarkdownText>
            </div>
          )}
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
