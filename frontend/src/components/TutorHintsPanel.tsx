import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { MathContent } from '@/components/MathContent'
import { suggestTutorHints, type TutorHints, type TutorLevel } from '@/lib/api'
import { errorMessage } from '@/lib/errors'

const LEVEL_TITLES: Record<number, string> = {
  1: 'Indiciu',
  2: 'Indiciu mai mare',
  3: 'Rezolvare completă',
}

/** Why the tutor stopped, in the teacher's words (the codes come from the tutor service). */
const REFUSAL_REASONS: Record<string, string> = {
  no_passages: 'Capitolul acestei întrebări nu are încă text încărcat pentru tutor.',
  too_far: 'Nicio pagină din manual nu e destul de aproape de ce cere întrebarea.',
  not_covered: 'Paginile găsite nu acoperă ce cere întrebarea.',
  no_citation: 'Răspunsul nu a citat nicio pagină din manual, așa că nu ți-l arăt.',
}

/**
 * "Sugerează indicii (AI)" in the question editor: the tutor's hint, bigger hint and full solution for this question,
 * written only from the EN chapter pages, each with the pages it used - or "Nu am asta în manual.". Nothing is applied
 * by itself: the teacher adds the levels they keep to the hints (or the solution), edits them, and saves the question.
 */
export function TutorHintsPanel({
  itemId,
  hintsLeft,
  canUseAsSolution,
  onAddHint,
  onUseAsSolution,
}: {
  itemId: number
  /** How many more hints the question may take. */
  hintsLeft: number
  /** Only while the solution box is empty, and never for an open question (there it is the barem). */
  canUseAsSolution: boolean
  onAddHint: (text: string) => void
  onUseAsSolution: (text: string) => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<TutorHints | null>(null)
  const [used, setUsed] = useState<Set<number>>(new Set())

  async function ask() {
    setBusy(true)
    setError(null)
    setResult(null)
    setUsed(new Set())
    try {
      setResult(await suggestTutorHints(itemId))
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  function use(level: TutorLevel, apply: (text: string) => void) {
    apply(level.text)
    setUsed((prev) => new Set(prev).add(level.level))
  }

  return (
    <div className="flex flex-col gap-2 rounded-md border border-dashed p-3" data-testid="tutor-hints">
      <div className="flex flex-wrap items-center gap-2">
        <Button type="button" variant="outline" size="sm" onClick={() => void ask()} disabled={busy}>
          {result ? 'Cere din nou (AI)' : 'Sugerează indicii (AI)'}
        </Button>
        <span className="text-xs text-muted-foreground">
          Citește întrebarea salvată și scrie doar din paginile capitolului EN; tu le verifici înainte să salvezi.
        </span>
      </div>

      {busy && (
        <p role="status" className="text-sm text-muted-foreground">Caut în manual și scriu indiciile…</p>
      )}
      {error && (
        <p role="alert" className="text-sm text-destructive">{error}</p>
      )}

      {result && (
        <div className="flex flex-col gap-3">
          {result.levels.map((level) => (
            <section key={level.level} className="flex flex-col gap-1 rounded-md bg-muted/40 p-2"
                     aria-label={LEVEL_TITLES[level.level] ?? `Treapta ${level.level}`}>
              <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                {level.level}. {LEVEL_TITLES[level.level] ?? `Treapta ${level.level}`}
              </h4>
              <MathContent className="text-sm">{level.text}</MathContent>
              {level.sources.length > 0 && (
                <p className="text-xs text-muted-foreground">
                  Din manual: {level.sources.map((s) => `${s.title}, pag. ${s.bookPage}`).join('; ')}
                </p>
              )}
              <div className="flex flex-wrap gap-2">
                <Button type="button" size="sm" variant="secondary"
                        disabled={used.has(level.level) || hintsLeft <= 0}
                        onClick={() => use(level, onAddHint)}>
                  {used.has(level.level) ? 'Adăugat' : 'Adaugă ca indiciu'}
                </Button>
                {level.level === 3 && canUseAsSolution && !used.has(level.level) && (
                  <Button type="button" size="sm" variant="ghost" onClick={() => use(level, onUseAsSolution)}>
                    Pune la rezolvare
                  </Button>
                )}
              </div>
            </section>
          ))}

          {result.refusal && (
            <div role="status" className="text-sm">
              <p className="font-medium">
                {result.levels.length > 0 ? `Treptele următoare: ${result.refusal}` : result.refusal}
              </p>
              {result.refusalReason && REFUSAL_REASONS[result.refusalReason] && (
                <p className="text-xs text-muted-foreground">{REFUSAL_REASONS[result.refusalReason]}</p>
              )}
            </div>
          )}

          {result.answered && (
            <p className="text-xs text-muted-foreground">
              Elevii văd indiciile doar după ce le adaugi și salvezi întrebarea.
              {result.costUsd > 0 && ` Cost: ~${result.costUsd.toFixed(4).replace('.', ',')} $.`}
            </p>
          )}
        </div>
      )}
    </div>
  )
}
