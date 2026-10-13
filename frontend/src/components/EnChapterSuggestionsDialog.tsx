import { useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { MathContent } from '@/components/MathContent'
import {
  assignEnChapters,
  suggestEnChapters,
  type EnChapterAssignment,
  type EnChapterSummary,
  type ItemChapterSuggestion,
  type QuizItemDto,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { numberItems } from '@/lib/sections'

/** One proposal the teacher may keep: for a question (partId null) or one of its requirements. */
interface Row {
  key: string
  itemId: number
  partId: number | null
  /** "I.3" or "III.6 b)" */
  label: string
  text: string
  /** Where it points now: the teacher's choice, else automatic. */
  currentId: number | null
  currentIsAuto: boolean
  proposedId: number
  reason: string | null
}

function rowsOf(items: QuizItemDto[], suggestions: ItemChapterSuggestion[]): Row[] {
  const rows: Row[] = []
  for (const { item, label } of numberItems(items)) {
    const s = suggestions.find((x) => x.itemId === item.id)
    if (!s) continue
    const parts = item.parts ?? []
    if (parts.length === 0 && s.chapterId !== null) {
      rows.push({
        key: `i${item.id}`, itemId: item.id, partId: null, label, text: item.statement,
        currentId: item.enChapterId ?? item.enChapterAuto ?? null, currentIsAuto: item.enChapterId == null,
        proposedId: s.chapterId, reason: s.reason,
      })
    }
    parts.forEach((part) => {
      const ps = s.parts.find((x) => x.partId === part.id)
      if (!ps || ps.chapterId === null) return
      rows.push({
        key: `p${part.id}`, itemId: item.id, partId: part.id, label: `${label} ${part.label})`, text: part.statement,
        currentId: part.enChapterId ?? part.enChapterAuto ?? null, currentIsAuto: part.enChapterId == null,
        proposedId: ps.chapterId, reason: ps.reason,
      })
    })
  }
  return rows
}

/**
 * "Sugerează capitolele (AI)": asks the AI for each question's EN chapter, shows the proposals next to what each
 * question points at now, and applies only the ones the teacher keeps (ticked by default where they differ).
 */
export function EnChapterSuggestionsDialog({ quizId, items, enChapters, onClose, onApplied }: {
  quizId: number
  items: QuizItemDto[]
  enChapters: EnChapterSummary[]
  onClose: () => void
  onApplied: () => Promise<void> | void
}) {
  // The questions as they were when the dialog opened: a reload behind it must not ask the AI (and pay) again.
  const [questions] = useState(items)
  const [rows, setRows] = useState<Row[] | null>(null)
  const [kept, setKept] = useState<Set<string>>(new Set())
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  // One paid call per opening: StrictMode runs the effect twice, and both runs must share the same request.
  const request = useRef<Promise<ItemChapterSuggestion[]> | null>(null)

  useEffect(() => {
    let cancelled = false
    request.current ??= suggestEnChapters(quizId)
    request.current
      .then((suggestions) => {
        if (cancelled) return
        const next = rowsOf(questions, suggestions)
        setRows(next)
        setKept(new Set(next.filter((r) => r.proposedId !== r.currentId).map((r) => r.key)))
      })
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
    }
  }, [quizId, questions])

  const chapter = (id: number | null) => (id === null ? null : enChapters.find((c) => c.id === id) ?? null)
  const name = (id: number | null) => {
    const c = chapter(id)
    return c ? `${c.code} ${c.title}` : '—'
  }
  const toggle = (key: string) =>
    setKept((current) => {
      const next = new Set(current)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })

  async function apply() {
    if (!rows) return
    const byItem = new Map<number, EnChapterAssignment>()
    for (const row of rows.filter((r) => kept.has(r.key))) {
      const item = questions.find((i) => i.id === row.itemId)!
      const assignment = byItem.get(row.itemId) ?? { itemId: row.itemId, enChapterId: item.enChapterId ?? null, parts: [] }
      if (row.partId === null) {
        assignment.enChapterId = row.proposedId
      } else {
        assignment.parts!.push({ partId: row.partId, enChapterId: row.proposedId })
      }
      byItem.set(row.itemId, assignment)
    }
    setBusy(true)
    setError(null)
    try {
      await assignEnChapters(quizId, [...byItem.values()])
      await onApplied()
      onClose()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title="Capitole EN propuse de AI" wide
            description="Bifează ce păstrezi. Nimic nu se schimbă până nu apeși „Aplică”.">
      <div className="space-y-3">
        {!rows && !error && <p className="text-sm text-muted-foreground" role="status">AI-ul citește întrebările…</p>}
        {rows && rows.length === 0 && <p className="text-sm text-muted-foreground">AI-ul nu a propus niciun capitol.</p>}
        {rows && rows.length > 0 && (
          <ul className="divide-y rounded-lg border">
            {rows.map((row) => {
              const same = row.proposedId === row.currentId
              return (
                <li key={row.key} className="flex gap-3 px-3 py-2" data-testid="en-suggestion">
                  <input type="checkbox" className="mt-1" checked={kept.has(row.key)} disabled={same}
                         onChange={() => toggle(row.key)} aria-label={`Păstrez propunerea pentru ${row.label}`} />
                  <div className="min-w-0 flex-1 space-y-1 text-sm">
                    <p className="font-medium">{row.label}</p>
                    <MathContent className="line-clamp-2 text-muted-foreground">{row.text}</MathContent>
                    <p>
                      Acum: {name(row.currentId)}{row.currentIsAuto && row.currentId !== null ? ' (automat)' : ''}
                      {' → '}
                      <span className="font-medium">{same ? 'la fel' : name(row.proposedId)}</span>
                    </p>
                    {row.reason && <p className="text-xs text-muted-foreground">{row.reason}</p>}
                  </div>
                </li>
              )
            })}
          </ul>
        )}
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Închide</Button>
          <Button type="button" onClick={() => void apply()} disabled={busy || !rows || kept.size === 0}>
            {busy ? 'Se aplică…' : `Aplică (${kept.size})`}
          </Button>
        </div>
      </div>
    </Dialog>
  )
}
