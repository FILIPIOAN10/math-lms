import { useState, type FormEvent } from 'react'
import { MathContent } from '@/components/MathContent'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  createQuizFromChapter as createQuizFromChapterRequest,
  type ChapterQuizInput,
  type Chapter,
  type Exercise,
  type QuizSummary,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'

interface Props {
  open: boolean
  onClose: () => void
  chapter: Chapter
  exercises: Exercise[]
  /** Called with the new draft, e.g. to open it in the editor. */
  onCreated: (quiz: QuizSummary) => void
  /** Injected in tests; defaults to the real API call. */
  createQuizFromChapter?: (chapterId: number, input: ChapterQuizInput) => Promise<QuizSummary>
}

/**
 * T2 "Creează quiz din capitol": tick exercises from the bank and get a draft quiz for the chapter's class, tagged with
 * the chapter. Each exercise becomes an open question (photo answer) with its solution as the barem; points and
 * answer options can be changed in the editor that opens next.
 */
export function ChapterQuizDialog({
  open,
  onClose,
  chapter,
  exercises,
  onCreated,
  createQuizFromChapter = createQuizFromChapterRequest,
}: Props) {
  const [title, setTitle] = useState(`${chapter.title} — test`)
  const [points, setPoints] = useState('1')
  const [picked, setPicked] = useState<Set<number>>(() => new Set(exercises.map((e) => e.id)))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const pointsNumber = Number(points)
  const pointsValid = Number.isInteger(pointsNumber) && pointsNumber >= 1 && pointsNumber <= 100
  const canSubmit = !busy && title.trim() !== '' && picked.size > 0 && pointsValid

  function toggle(id: number) {
    setPicked((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!canSubmit) return
    setError(null)
    setBusy(true)
    try {
      // The server keeps the chapter's order whatever the order of the ticks.
      const created = await createQuizFromChapter(chapter.id, {
        title: title.trim(),
        exerciseIds: exercises.filter((ex) => picked.has(ex.id)).map((ex) => ex.id),
        points: pointsNumber,
      })
      onCreated(created)
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => !o && onClose()}
      title="Creează test din capitol"
      description="Fiecare exercițiu bifat devine o întrebare cu rezolvare (elevul încarcă poza rezolvării), cu soluția ca barem. Testul e o ciornă pentru clasa capitolului; îl modifici și îl publici din editor."
    >
      <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
        <div className="flex flex-col gap-2">
          <Label htmlFor="chapter-quiz-title">Titlu</Label>
          <Input id="chapter-quiz-title" value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} />
        </div>

        <div className="space-y-2">
          <div className="flex items-center justify-between text-sm">
            <span className="font-medium">Exerciții ({picked.size} din {exercises.length})</span>
            <button
              type="button"
              className="text-muted-foreground hover:text-foreground hover:underline"
              onClick={() =>
                setPicked(picked.size === exercises.length ? new Set() : new Set(exercises.map((ex) => ex.id)))
              }
            >
              {picked.size === exercises.length ? 'Deselectează tot' : 'Selectează tot'}
            </button>
          </div>
          <ul className="max-h-60 divide-y divide-border overflow-y-auto rounded-lg border border-border">
            {exercises.map((ex, i) => (
              <li key={ex.id}>
                <label className="flex cursor-pointer items-start gap-2 px-3 py-2 text-sm">
                  <input
                    type="checkbox"
                    className="mt-1"
                    aria-label={`Exercițiul ${i + 1}`}
                    checked={picked.has(ex.id)}
                    disabled={busy}
                    onChange={() => toggle(ex.id)}
                  />
                  <span className="min-w-0">
                    <span className="font-medium">{i + 1}. </span>
                    <MathContent inline className="text-muted-foreground">{ex.statement}</MathContent>
                  </span>
                </label>
              </li>
            ))}
          </ul>
        </div>

        <div className="flex flex-col gap-2">
          <Label htmlFor="chapter-quiz-points">Puncte pe întrebare</Label>
          <Input
            id="chapter-quiz-points"
            type="number"
            min={1}
            max={100}
            className="w-28"
            value={points}
            onChange={(e) => setPoints(e.target.value)}
          />
          {!pointsValid && <p className="text-sm text-destructive">Alege un număr între 1 și 100.</p>}
        </div>

        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>
            Anulează
          </Button>
          <Button type="submit" disabled={!canSubmit}>
            {busy ? 'Se creează…' : `Creează testul (${picked.size})`}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
