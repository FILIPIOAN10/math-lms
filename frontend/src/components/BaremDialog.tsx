import { useEffect, useId, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { MathContent } from '@/components/MathContent'
import {
  applyBarem,
  createBaremRead,
  getBaremRead,
  type BaremEntry,
  type BaremItemProposal,
  type BaremProposal,
  type QuizItemDto,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { shrinkImage } from '@/lib/image'

/** How often the reading is asked for while the AI works (it may think for a minute or two). */
const POLL_MS = 3000

/** What the teacher has on screen for one question: the letter picked, and the barem texts as edited. */
interface Draft {
  letter: string | null
  barem: string
  parts: Record<number, string>
}

const letterOf = (index: number) => String.fromCharCode(97 + index)

function draftOf(p: BaremItemProposal): Draft {
  return {
    letter: p.proposedLetter ?? p.currentLetter,
    barem: p.proposedBarem ?? p.currentBarem ?? '',
    parts: Object.fromEntries(p.parts.map((part) => [part.partId, part.proposedBarem ?? part.currentBarem ?? ''])),
  }
}

/** What would be saved for this question, or null when nothing differs from what the test has now. */
function entryOf(p: BaremItemProposal, d: Draft, item: QuizItemDto | undefined, taken: boolean): BaremEntry | null {
  const entry: BaremEntry = { itemId: p.itemId }
  if (p.type === 'SINGLE_CHOICE' && !taken && d.letter !== null && d.letter !== p.currentLetter) {
    const option = [...(item?.options ?? [])].sort((a, b) => a.position - b.position)[d.letter.charCodeAt(0) - 97]
    if (option) entry.correctOptionId = option.id
  }
  if (p.parts.length === 0 && p.type === 'OPEN' && d.barem.trim() !== (p.currentBarem ?? '').trim()) {
    entry.barem = d.barem
  }
  const parts = p.parts
    .filter((part) => (d.parts[part.partId] ?? '').trim() !== (part.currentBarem ?? '').trim())
    .map((part) => ({ partId: part.partId, barem: d.parts[part.partId] ?? '' }))
  if (parts.length > 0) entry.parts = parts
  return Object.keys(entry).length > 1 ? entry : null
}

/**
 * "Barem din poze (AI)": the teacher photographs the official barem of this test, the AI reads it (in the background,
 * it may take a minute or two) and maps it onto the test's questions - the right letter of each grilă, the barem of
 * each requirement of Subiectul III. Everything stays editable, and nothing changes in the test before "Aplică".
 */
export function BaremDialog({ quizId, items, onClose, onApplied, pollMs = POLL_MS }: {
  quizId: number
  items: QuizItemDto[]
  onClose: () => void
  onApplied: () => Promise<void> | void
  /** Tests poll at once. */
  pollMs?: number
}) {
  const inputId = useId()
  const [photos, setPhotos] = useState<File[]>([])
  const [readId, setReadId] = useState<number | null>(null)
  const [proposal, setProposal] = useState<BaremProposal | null>(null)
  const [drafts, setDrafts] = useState<Record<number, Draft>>({})
  const [kept, setKept] = useState<Set<number>>(new Set())
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // While the AI reads: ask again every pollMs until DONE or FAILED.
  useEffect(() => {
    if (readId === null) return
    let cancelled = false
    let timer: ReturnType<typeof setTimeout>
    const poll = () => {
      getBaremRead(quizId, readId)
        .then((read) => {
          if (cancelled) return
          if (read.status === 'DONE' && read.proposal) {
            const next = read.proposal
            const nextDrafts = Object.fromEntries(next.items.map((p) => [p.itemId, draftOf(p)]))
            setProposal(next)
            setDrafts(nextDrafts)
            setKept(new Set(next.items
              .filter((p) => entryOf(p, nextDrafts[p.itemId], items.find((i) => i.id === p.itemId), next.taken) !== null)
              .map((p) => p.itemId)))
            setReadId(null)
          } else if (read.status === 'FAILED') {
            setError(read.error ?? 'Nu am putut citi baremul.')
            setReadId(null)
          } else {
            timer = setTimeout(poll, pollMs)
          }
        })
        .catch((e) => {
          if (cancelled) return
          setError(errorMessage(e))
          setReadId(null)
        })
    }
    timer = setTimeout(poll, pollMs)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [readId, quizId, pollMs, items])

  async function read() {
    setBusy(true)
    setError(null)
    try {
      const shrunk = await Promise.all(photos.map((p) => shrinkImage(p, 2000, 0.85)))
      const started = await createBaremRead(quizId, shrunk)
      setReadId(started.id)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const entries = proposal
    ? proposal.items
      .filter((p) => kept.has(p.itemId))
      .map((p) => entryOf(p, drafts[p.itemId], items.find((i) => i.id === p.itemId), proposal.taken))
      .filter((e): e is BaremEntry => e !== null)
    : []

  async function apply() {
    setBusy(true)
    setError(null)
    try {
      await applyBarem(quizId, entries)
      await onApplied()
      onClose()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const setDraft = (itemId: number, change: Partial<Draft>) =>
    setDrafts((all) => ({ ...all, [itemId]: { ...all[itemId], ...change } }))
  const toggle = (itemId: number) =>
    setKept((current) => {
      const next = new Set(current)
      if (next.has(itemId)) next.delete(itemId)
      else next.add(itemId)
      return next
    })

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title="Barem din poze (AI)" wide
            description="Fotografiază baremul oficial al testului. AI-ul îl potrivește cu întrebările; verifici și apeși „Aplică”.">
      <div className="space-y-3">
        {!proposal && (
          <div className="space-y-2">
            <label htmlFor={inputId} className="text-sm font-medium">Pozele cu baremul</label>
            <input
              id={inputId}
              type="file"
              accept="image/*"
              multiple
              disabled={busy || readId !== null}
              onChange={(e) => setPhotos([...(e.target.files ?? [])])}
              className="block w-full text-sm file:mr-3 file:rounded-lg file:border file:bg-background file:px-3 file:py-1.5"
            />
            <p className="text-xs text-muted-foreground">
              Câte o pagină pe poză, dreaptă și luminată (sau capturi de ecran din PDF-ul baremului). Pozele se șterg după
              citire.
            </p>
            {readId !== null && (
              <p role="status" className="text-sm text-muted-foreground">
                AI-ul citește baremul… poate dura un minut sau două.
              </p>
            )}
          </div>
        )}

        {proposal && proposal.items.length === 0 && (
          <p className="text-sm text-muted-foreground">Baremul nu se potrivește cu nicio întrebare a testului.</p>
        )}
        {proposal && proposal.items.length > 0 && (
          <ul className="divide-y rounded-lg border">
            {proposal.items.map((p) => {
              const d = drafts[p.itemId]
              const item = items.find((i) => i.id === p.itemId)
              const options = [...(item?.options ?? [])].sort((a, b) => a.position - b.position)
              const changes = entryOf(p, d, item, proposal.taken) !== null
              return (
                <li key={p.itemId} className="flex gap-3 px-3 py-3" data-testid="barem-row">
                  <input type="checkbox" className="mt-1" checked={kept.has(p.itemId)} disabled={!changes}
                         onChange={() => toggle(p.itemId)} aria-label={`Aplic baremul la ${p.label}`} />
                  <div className="min-w-0 flex-1 space-y-2 text-sm">
                    <p className="font-medium">
                      {p.label} <span className="font-normal text-muted-foreground">· {p.points} p</span>
                    </p>
                    <MathContent className="line-clamp-2 text-muted-foreground">{p.statement}</MathContent>
                    {p.type === 'SINGLE_CHOICE' && (
                      <div className="flex flex-wrap items-center gap-2">
                        <span>Acum: {p.currentLetter ? `${p.currentLetter})` : 'nicio variantă bifată'}</span>
                        <span aria-hidden="true">→</span>
                        <select
                          aria-label={`Varianta corectă la ${p.label}`}
                          value={d.letter ?? ''}
                          disabled={proposal.taken}
                          onChange={(e) => setDraft(p.itemId, { letter: e.target.value })}
                          className="h-8 rounded-lg border border-border bg-background px-2 text-sm max-sm:h-11"
                        >
                          {options.map((o, i) => (
                            <option key={o.id} value={letterOf(i)}>{letterOf(i)}) {o.text}</option>
                          ))}
                        </select>
                        {proposal.taken && (
                          <span className="text-xs text-muted-foreground">
                            Testul a fost dat deja: varianta corectă nu se mai schimbă.
                          </span>
                        )}
                      </div>
                    )}
                    {p.type === 'OPEN' && p.parts.length === 0 && (
                      <Textarea aria-label={`Barem ${p.label}`} value={d.barem} className="min-h-20 font-mono text-xs"
                                onChange={(e) => setDraft(p.itemId, { barem: e.target.value })} />
                    )}
                    {p.parts.map((part) => (
                      <div key={part.partId} className="space-y-1">
                        <p className="text-xs">
                          <span className="font-semibold">{part.label})</span> {part.statement}{' '}
                          <span className="text-muted-foreground">({part.points}p)</span>
                        </p>
                        <Textarea aria-label={`Barem ${p.label} ${part.label})`} value={d.parts[part.partId] ?? ''}
                                  className="min-h-16 font-mono text-xs"
                                  onChange={(e) => setDraft(p.itemId, { parts: { ...d.parts, [part.partId]: e.target.value } })} />
                      </div>
                    ))}
                    {p.warnings.map((w) => (
                      <p key={w} className="text-xs font-medium text-amber-700 dark:text-amber-300">⚠ {w}</p>
                    ))}
                  </div>
                </li>
              )
            })}
          </ul>
        )}
        {proposal && proposal.unmatched.length > 0 && (
          <p className="text-xs text-muted-foreground">
            În barem, dar nu în test: {proposal.unmatched.join(', ')}.
          </p>
        )}

        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Închide</Button>
          {proposal ? (
            <Button type="button" onClick={() => void apply()} disabled={busy || entries.length === 0}>
              {busy ? 'Se aplică…' : `Aplică baremul (${entries.length})`}
            </Button>
          ) : (
            <Button type="button" onClick={() => void read()} disabled={busy || readId !== null || photos.length === 0}>
              Citește baremul
            </Button>
          )}
        </div>
      </div>
    </Dialog>
  )
}
