import { useEffect, useState } from 'react'
import {
  getShuffle as getShuffleRequest,
  setShuffle as setShuffleRequest,
  type ShuffleSettings,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'

/**
 * "Ordine diferită la fiecare elev" (Moodle "Shuffle questions" / "Shuffle within questions"): two switches on a test.
 * Each attempt keeps the order it started with, so turning them mid-test reorders nobody's paper; grading, the result
 * and the statistics stay in the teacher's order. Shows nothing until the switches are loaded.
 */
export function ShuffleSwitches({
  quizId,
  getShuffle = getShuffleRequest,
  setShuffle = setShuffleRequest,
}: {
  quizId: number
  getShuffle?: typeof getShuffleRequest
  setShuffle?: typeof setShuffleRequest
}) {
  const [settings, setSettings] = useState<ShuffleSettings | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getShuffle(quizId)
      .then((s) => !cancelled && setSettings(s))
      .catch(() => undefined) // no switches shown; the test works in its own order
    return () => {
      cancelled = true
    }
  }, [quizId, getShuffle])

  if (settings === null) return null

  async function change(next: ShuffleSettings) {
    setBusy(true)
    setError(null)
    try {
      setSettings(await setShuffle(quizId, next))
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <fieldset data-testid="shuffle-switches" className="space-y-1 rounded-lg border p-3 text-sm">
      <legend className="px-1 font-medium">Ordine diferită la fiecare elev</legend>
      <label className="flex min-h-11 items-center gap-2">
        <input type="checkbox" className="size-4" checked={settings.items} disabled={busy}
               onChange={(e) => void change({ ...settings, items: e.target.checked })} />
        Amestecă întrebările (fiecare rămâne în subiectul ei)
      </label>
      <label className="flex min-h-11 items-center gap-2">
        <input type="checkbox" className="size-4" checked={settings.options} disabled={busy}
               onChange={(e) => void change({ ...settings, options: e.target.checked })} />
        Amestecă variantele de la grile
      </label>
      <p className="text-xs text-muted-foreground">
        Colegii de bancă primesc altă ordine; la reluare, fiecare își vede aceeași ordine ca la început. Corectarea și
        statisticile rămân în ordinea ta. Nu amesteca variantele dacă una spune „toate cele de mai sus”.
      </p>
      {error && <p className="text-xs text-destructive">{error}</p>}
    </fieldset>
  )
}
